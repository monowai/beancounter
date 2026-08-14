package com.beancounter.marketdata.providers.eodhd

import com.beancounter.common.input.AssetInput
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.NewsProvider
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import com.beancounter.marketdata.providers.eodhd.news.NewsArticle
import com.beancounter.marketdata.providers.eodhd.news.NewsArticleRepo
import com.beancounter.marketdata.providers.eodhd.news.NewsArticleTicker
import com.beancounter.marketdata.providers.eodhd.news.NewsFetch
import com.beancounter.marketdata.providers.eodhd.news.NewsFetchRepo
import jakarta.transaction.Transactional
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import kotlin.math.abs

/**
 * DB-backed EODHD news adapter. The read path always returns from the [NewsArticle] table; the
 * write path only re-hits EODHD when the per-ticker entry in [NewsFetch] is older than
 * `eodhd.news.refresh-after-hours` (default 6h). Articles persist for `retention-days` (default 30)
 * and the daily [com.beancounter.marketdata.providers.eodhd.news.NewsRetentionSchedule] prunes
 * anything older.
 *
 * The projection back to the caller keeps the AV-compatible `{feed, count}` shape so
 * svc-agent's NewsTools doesn't change. EODHD-specific fields (`polarity`, `tags`, `symbols`) are
 * also returned because they're useful context for the LLM.
 */
@Service
class EodhdNewsService(
    private val eodhdProxy: EodhdProxy,
    private val eodhdConfig: EodhdConfig,
    private val newsProperties: EodhdNewsProperties,
    private val newsArticleRepo: NewsArticleRepo,
    private val newsFetchRepo: NewsFetchRepo,
    private val assetFinder: AssetFinder,
    private val dateUtils: DateUtils = DateUtils()
) : NewsProvider {
    private val log = LoggerFactory.getLogger(EodhdNewsService::class.java)

    // Bounds the upstream fan-out so a large holdings list can't fire one EODHD request per ticker
    // simultaneously — that would amplify load and trip the shared ~1200/day quota in a single burst.
    private val fetchGate = Semaphore(MAX_CONCURRENT_FETCHES)

    @Transactional
    override fun getNewsSentiment(
        tickers: String,
        market: String?,
        topics: String?
    ): Map<String, Any> = newsForSymbols(resolveSymbols(tickers, market), topics)

    /**
     * Market / sector news via proxy symbols. svc-agent passes already-resolved EODHD symbols
     * (e.g. `GSPC.INDX` for the S&P 500, `XLK.US` for the tech sector SPDR) so macro and sector-wide
     * moves surface even when no held ticker is tagged in the article. Symbols are verbatim — unlike
     * [getNewsSentiment] there's no ticker+market resolution step — and otherwise share the same
     * DB-backed refresh / rank / project pipeline.
     */
    @Transactional
    override fun getMarketNews(
        symbols: List<String>,
        topics: String?
    ): Map<String, Any> = newsForSymbols(symbols.map { it.trim().uppercase() }.filter { it.isNotBlank() }, topics)

    /**
     * Shared read path: refresh any stale symbols from upstream, then rank the stored articles and
     * project to the `{feed, count}` shape. Returns an empty map when no symbols resolve or nothing
     * ranks — the no-coverage signal svc-agent's NewsTools expects.
     */
    private fun newsForSymbols(
        symbols: List<String>,
        topics: String?
    ): Map<String, Any> {
        if (symbols.isEmpty()) return emptyMap()

        val refreshCutoff = LocalDateTime.now(dateUtils.zoneId).minusHours(newsProperties.refreshAfterHours)
        refreshStaleSymbols(symbols.filter { shouldRefresh(it, refreshCutoff) })

        val retentionStart = LocalDateTime.now(dateUtils.zoneId).minusDays(newsProperties.retentionDays)
        val stored = newsArticleRepo.findByTickersAfter(symbols, retentionStart)
        val ranked = rank(stored, symbols, topics).map { project(it) }

        if (ranked.isEmpty()) return emptyMap()
        return mapOf(
            "feed" to ranked,
            "count" to ranked.size
        )
    }

    /**
     * Rank the stored window down to the articles worth spending prompt tokens on.
     *
     * Recency leads. EODHD polarity saturates at ±1.0 across a large share of the feed, so ranking
     * on magnitude alone was an arbitrary tie-break that floated weeks-old articles above the ones
     * explaining today's move — a holding could drop 8% and the agent would see a month-old
     * industry round-up instead. Polarity now only separates articles published at the same instant.
     *
     * Selection is round-robin across the requested symbols: each takes its newest article before
     * any takes a second. A portfolio briefing requests every holding in one call, and a flat
     * top-N let one well-covered mega-cap consume the whole budget — leaving the LLM with nothing
     * on the holding that actually moved, which it then filled in from training data.
     */
    private fun rank(
        stored: List<NewsArticle>,
        symbols: List<String>,
        topics: String?
    ): List<NewsArticle> {
        val eligible = stored.filter { topics.isNullOrBlank() || matchesTopic(it, topics) }
        if (eligible.isEmpty()) return emptyList()

        val perSymbol =
            symbols.map { symbol ->
                eligible
                    .filter { article -> article.tickerLinks.any { it.ticker == symbol } }
                    .sortedWith(NEWEST_FIRST)
                    .take(newsProperties.maxArticlesPerSymbol)
            }

        // Keyed by id so an article tagged to several requested holdings is only counted once.
        val picked = LinkedHashMap<String, NewsArticle>()
        rounds@ for (round in 0 until newsProperties.maxArticlesPerSymbol) {
            for (candidates in perSymbol) {
                if (picked.size >= newsProperties.maxArticles) break@rounds
                candidates.getOrNull(round)?.let { picked.putIfAbsent(it.id, it) }
            }
        }
        return picked.values.sortedWith(NEWEST_FIRST).take(newsProperties.maxArticles)
    }

    private fun shouldRefresh(
        symbol: String,
        cutoff: LocalDateTime
    ): Boolean {
        val meta = newsFetchRepo.findById(symbol).orElse(null)
        return meta == null || meta.lastFetchedAt.isBefore(cutoff)
    }

    /**
     * Refresh each stale symbol from EODHD. The upstream HTTP call is the slow part — one network
     * round-trip per symbol — so the fetches fan out across [Dispatchers.IO] and run concurrently.
     * A 10-ticker agent query that previously did 10 serial round-trips (~21s observed in Sentry)
     * collapses to a single round-trip's latency.
     *
     * Persistence stays on the calling thread. This method runs inside a `@Transactional` boundary,
     * and the bound Hibernate session is single-threaded — DB writes must never touch it from a
     * coroutine dispatcher thread. So coroutines do network-only work; the results are persisted
     * serially here. [runBlockingTraced] keeps the OTel/Sentry span attached across the dispatcher
     * switch (see jar-common CoroutineTracing).
     */
    private fun refreshStaleSymbols(symbols: List<String>) {
        if (symbols.isEmpty()) return
        val fetched =
            runBlockingTraced {
                symbols
                    .map { symbol ->
                        async(Dispatchers.IO) { fetchGate.withPermit { symbol to fetchUpstream(symbol) } }
                    }.awaitAll()
            }
        for ((symbol, outcome) in fetched) {
            persist(symbol, outcome)
        }
    }

    private fun fetchUpstream(symbol: String): FetchResult =
        try {
            FetchResult.Success(
                eodhdProxy.getNews(
                    symbol = symbol,
                    limit = newsProperties.providerLimit,
                    from = null,
                    apiKey = eodhdConfig.apiKey
                )
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("EODHD news lookup failed for {}: {}", symbol, e.message)
            FetchResult.Failure
        }

    private fun persist(
        symbol: String,
        outcome: FetchResult
    ) {
        when (outcome) {
            is FetchResult.Success -> {
                upsertAll(symbol, outcome.articles)
                newsFetchRepo.save(NewsFetch(symbol, LocalDateTime.now(dateUtils.zoneId), outcome.articles.size))
            }
            FetchResult.Failure -> {
                // Burn the refresh cooldown even on failure — otherwise a transient EODHD outage
                // or 429 turns every subsequent request into a quota-amplifying retry storm. Next
                // attempt waits `refresh-after-hours`, matching the success-path backoff. Articles
                // already in the DB keep serving in the meantime.
                val existing =
                    newsFetchRepo
                        .findById(symbol)
                        .orElseGet { NewsFetch(ticker = symbol, lastFetchedAt = LocalDateTime.now(dateUtils.zoneId)) }
                existing.lastFetchedAt = LocalDateTime.now(dateUtils.zoneId)
                newsFetchRepo.save(existing)
            }
        }
    }

    private sealed interface FetchResult {
        data class Success(
            val articles: List<EodhdNewsArticle>
        ) : FetchResult

        data object Failure : FetchResult
    }

    private fun upsertAll(
        symbol: String,
        fresh: List<EodhdNewsArticle>
    ) {
        for (incoming in fresh) {
            val externalId = incoming.link.ifBlank { "${incoming.date}|${incoming.title}" }
            saveOrMerge(symbol, externalId, incoming)
        }
    }

    /**
     * Defensive upsert: read-then-save can race when two concurrent refreshes for overlapping
     * tickers find the same `external_id` missing and both try to insert. The DB unique constraint
     * catches one transaction with a [DataIntegrityViolationException]; we then re-fetch the row
     * the winner inserted and merge our fields into it. Idempotent — at worst we do one extra read
     * + write on contention.
     */
    private fun saveOrMerge(
        symbol: String,
        externalId: String,
        incoming: EodhdNewsArticle
    ) {
        val existing = newsArticleRepo.findByExternalId(externalId).orElse(null)
        val article =
            existing ?: NewsArticle(
                externalId = externalId,
                // Placeholder — applyIncoming() overwrites both stamps immediately below.
                published = LocalDateTime.now(dateUtils.zoneId),
                fetchedAt = LocalDateTime.now(dateUtils.zoneId)
            )
        applyIncoming(article, externalId, symbol, incoming)
        try {
            newsArticleRepo.save(article)
        } catch (e: DataIntegrityViolationException) {
            val winner = newsArticleRepo.findByExternalId(externalId).orElseThrow { e }
            applyIncoming(winner, externalId, symbol, incoming)
            newsArticleRepo.save(winner)
        }
    }

    private fun applyIncoming(
        article: NewsArticle,
        externalId: String,
        symbol: String,
        incoming: EodhdNewsArticle
    ) {
        article.externalId = externalId
        article.published = parsePublished(incoming.date)
        article.title = incoming.title
        article.content = incoming.content
        article.link = incoming.link
        incoming.sentiment?.let { s ->
            article.polarity = s.polarity
            article.sentimentPos = s.pos
            article.sentimentNeg = s.neg
            article.sentimentNeu = s.neu
        }
        article.source = "EODHD"
        article.fetchedAt = LocalDateTime.now(dateUtils.zoneId)

        // Refresh the tag set — EODHD can revise tags between fetches.
        article.tags.clear()
        article.tags.addAll(incoming.tags)

        // Symbols: keep raw EODHD tickers + ensure the queried symbol is always present so a
        // ticker that only appears in `symbols[]` on one update isn't lost.
        val symbolSet = (incoming.symbols + symbol).toSet()
        val existingTickers = article.tickerLinks.map { it.ticker }.toSet()
        for (t in symbolSet - existingTickers) {
            article.tickerLinks.add(NewsArticleTicker(ticker = t))
        }
    }

    private fun parsePublished(raw: String): LocalDateTime =
        try {
            // EODHD ships `2026-05-15T05:15:00+00:00` — keep an OffsetDateTime parse for safety.
            OffsetDateTime.parse(raw).toLocalDateTime()
        } catch (
            @Suppress("SwallowedException")
            e: DateTimeParseException
        ) {
            try {
                LocalDateTime.parse(raw)
            } catch (
                @Suppress("SwallowedException")
                e2: DateTimeParseException
            ) {
                LocalDateTime.now(ZoneOffset.UTC)
            }
        }

    private fun resolveSymbols(
        tickers: String,
        market: String?
    ): List<String> {
        val exchange =
            if (market.isNullOrBlank()) {
                "US"
            } else {
                // Caller-supplied market codes are not guaranteed to be ours, and
                // MarketService is @Transactional — catching its exception left the
                // caller's transaction rollback-only (#1088).
                eodhdConfig.marketService
                    .getMarketOrNull(market)
                    ?.getAlias(EodhdPriceService.ID)
                    ?: market.also { log.debug("No EODHD alias for market {}", it) }
            }
        val bcMarket = if (market.isNullOrBlank()) "US" else market
        return tickers
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.matches(TICKER_PATTERN) }
            .map { "${priceSymbolFor(it, bcMarket)}.$exchange" }
    }

    /**
     * Map a BC asset code to the symbol EODHD actually indexes news under. BC stores the canonical
     * EODHD ticker in `Asset.priceSymbol` (e.g. code `BRK.B` → priceSymbol `BRK-B`, since EODHD uses
     * a hyphen for US class shares). Without this, a naive `code.exchange` build (`BRK.B.US`) has no
     * EODHD coverage and the agent wrongly falls back to general knowledge. Unknown tickers (not held
     * locally) and lookup failures fall back to the raw code so ad-hoc queries still work.
     */
    private fun priceSymbolFor(
        code: String,
        bcMarket: String
    ): String =
        // findLocally now returns null for an unknown market instead of throwing, so the
        // "not held locally" fallback no longer needs a catch that would have left the
        // caller's transaction rollback-only (#1088).
        assetFinder
            .findLocally(AssetInput(bcMarket, code))
            ?.priceSymbol
            ?.takeIf { it.isNotBlank() }
            ?: code.also { log.debug("No local asset for {}/{}", bcMarket, it) }

    private fun matchesTopic(
        article: NewsArticle,
        topic: String
    ): Boolean {
        val needle = topic.trim().uppercase()
        return article.tags.any { it.uppercase() == needle }
    }

    private fun project(article: NewsArticle): Map<String, Any> {
        val polarity = article.polarity.toDouble()
        return mapOf(
            "title" to article.title,
            "summary" to (article.summary?.takeIf { it.isNotBlank() } ?: article.content.take(SUMMARY_CHARS)),
            "source" to article.link,
            "timePublished" to article.published.toString(),
            "sentimentLabel" to labelFor(polarity),
            "sentimentScore" to scaledScore(polarity),
            "relevance" to 1.0,
            "tickerSentimentLabel" to labelFor(polarity),
            "tickerSentimentScore" to scaledScore(polarity),
            "polarity" to polarity,
            "symbols" to article.tickerLinks.map { it.ticker },
            "tags" to article.tags.toList()
        )
    }

    private fun scaledScore(polarity: Double): Double =
        BigDecimal(polarity).setScale(4, RoundingMode.HALF_UP).toDouble()

    private fun labelFor(polarity: Double): String =
        when {
            polarity >= BULLISH_THRESHOLD -> "Bullish"
            polarity <= BEARISH_THRESHOLD -> "Bearish"
            else -> "Neutral"
        }

    companion object {
        // Caps simultaneous EODHD news round-trips per request. 8 covers a typical portfolio's
        // holdings in one wave while leaving headroom under the shared daily quota.
        private const val MAX_CONCURRENT_FETCHES = 8

        // Recency first; polarity magnitude only separates articles sharing a publish instant.
        // externalId is the final tie-break so a given window always ranks the same way.
        private val NEWEST_FIRST =
            compareByDescending<NewsArticle> { it.published }
                .thenByDescending { abs(it.polarity.toDouble()) }
                .thenBy { it.externalId }

        private const val SUMMARY_CHARS = 400
        private const val BULLISH_THRESHOLD = 0.35
        private const val BEARISH_THRESHOLD = -0.35
        private val TICKER_PATTERN = Regex("[A-Z0-9.-]{1,10}")
    }
}