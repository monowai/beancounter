package com.beancounter.marketdata.news.eodhd

import com.beancounter.common.input.AssetInput
import com.beancounter.common.telemetry.runBlockingTraced
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.news.EmbeddingCodec
import com.beancounter.marketdata.news.NewsArticle
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.NewsArticleTicker
import com.beancounter.marketdata.news.NewsEmbedder
import com.beancounter.marketdata.news.NewsEmbeddingProperties
import com.beancounter.marketdata.news.NewsFetch
import com.beancounter.marketdata.news.NewsFetchRepo
import com.beancounter.marketdata.news.NewsProvider
import com.beancounter.marketdata.news.TopicAnchorStore
import com.beancounter.marketdata.news.VectorMath
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
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
 * and the daily [com.beancounter.marketdata.news.NewsRetentionSchedule] prunes
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
    private val newsEmbedder: NewsEmbedder,
    private val embeddingProperties: NewsEmbeddingProperties,
    private val topicAnchorStore: TopicAnchorStore,
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
     * Broad macro/topic news (`stock markets`, `economy`, `inflation`, …) rather than news pinned
     * to a ticker or index. Each topic maps to a synthetic key (`TOPIC:STOCK_MARKETS`) that reuses
     * the exact same DB-backed refresh / cooldown / rank / project pipeline as ticker news — the
     * key is stored as the article's "ticker" tag via [applyIncoming], so [NewsArticleRepo.findByTickersAfter]
     * finds it the same way it finds a held symbol.
     */
    @Transactional
    override fun getTopicNews(topics: List<String>): Map<String, Any> {
        // Map key -> original (untruncated) topic so refreshStaleSymbols can recover the full
        // EODHD tag even when the key itself was truncated to fit the ticker columns (see
        // topicKey/topicTagFor). Built via associateBy so a key collision after truncation just
        // keeps the last topic that produced it, rather than fetching for both.
        val keyToTopic =
            topics
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .associateBy { topicKey(it) }
        return newsForSymbols(keyToTopic.keys.toList(), null, keyToTopic)
    }

    /**
     * Shared read path: refresh any stale symbols from upstream, then rank the stored articles and
     * project to the `{feed, count}` shape. Returns an empty map when no symbols resolve or nothing
     * ranks — the no-coverage signal svc-agent's NewsTools expects.
     */
    private fun newsForSymbols(
        symbols: List<String>,
        topics: String?,
        topicTags: Map<String, String> = emptyMap()
    ): Map<String, Any> {
        if (symbols.isEmpty()) return emptyMap()

        val refreshCutoff = LocalDateTime.now(dateUtils.zoneId).minusHours(newsProperties.refreshAfterHours)
        refreshStaleSymbols(symbols.filter { shouldRefresh(it, refreshCutoff) }, topicTags)

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
        val eligible =
            suppressNearDuplicates(stored.filter { topics.isNullOrBlank() || matchesTopic(it, topics) })
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

    /**
     * Greedy near-duplicate suppression so template-spam articles ("X fell more than the market"
     * ×N from different wire services) can't flood the top-N. Runs purely off already-persisted
     * [NewsArticle.embedding] vectors — independent of whether [newsEmbedder] is currently active,
     * so a vector computed while the feature was on still dedups correctly after it's switched
     * back off. Zero-cost, and a behavioral no-op, when nothing in [eligible] carries an embedding
     * yet (today's default, and every window before this feature shipped).
     *
     * Sorted newest-first before clustering, so the representative kept for a duplicate group is
     * always the newest article. Articles without a stored embedding are always kept — there's
     * nothing to compare them against, so silently dropping one on a false-suspicion basis would
     * be strictly worse than the pre-dedup behavior.
     *
     * O(n^2) against the kept-representative set — fine at the retention window's scale (a few
     * hundred articles at most).
     */
    private fun suppressNearDuplicates(eligible: List<NewsArticle>): List<NewsArticle> {
        if (eligible.none { it.embedding != null }) return eligible

        val kept = mutableListOf<FloatArray>()
        val result = mutableListOf<NewsArticle>()
        for (article in eligible.sortedWith(NEWEST_FIRST)) {
            val vector = article.embedding?.let { EmbeddingCodec.decode(it) }
            if (vector == null) {
                result.add(article)
                continue
            }
            val isDuplicate =
                kept.any { representative ->
                    VectorMath.dot(vector, representative) >=
                        embeddingProperties.similarityThreshold
                }
            if (!isDuplicate) {
                kept.add(vector)
                result.add(article)
            }
        }
        return result
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
    private fun refreshStaleSymbols(
        symbols: List<String>,
        topicTags: Map<String, String> = emptyMap()
    ) {
        if (symbols.isEmpty()) return
        val fetched =
            runBlockingTraced {
                symbols
                    .map { symbol ->
                        async(Dispatchers.IO) { fetchGate.withPermit { symbol to fetchFor(symbol, topicTags) } }
                    }.awaitAll()
            }
        for ((symbol, outcome) in fetched) {
            persist(symbol, outcome)
        }
    }

    /** Dispatch by key shape: a synthetic `TOPIC:` key fetches by topic tag, anything else by ticker. */
    private fun fetchFor(
        key: String,
        topicTags: Map<String, String> = emptyMap()
    ): FetchResult = if (key.startsWith(TOPIC_PREFIX)) fetchUpstreamTopic(key, topicTags) else fetchUpstream(key)

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

    private fun fetchUpstreamTopic(
        key: String,
        topicTags: Map<String, String> = emptyMap()
    ): FetchResult =
        try {
            val from = dateUtils.date.minusDays(newsProperties.topicWindowDays).toString()
            FetchResult.Success(
                eodhdProxy.getNewsByTopic(
                    topic = topicTagFor(key, topicTags),
                    limit = newsProperties.providerLimit,
                    from = from,
                    apiKey = eodhdConfig.apiKey
                )
            )
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("EODHD topic news lookup failed for {}: {}", key, e.message)
            FetchResult.Failure
        }

    /**
     * "stock markets" -> "TOPIC:STOCK_MARKETS". Truncated to [TOPIC_KEY_MAX_SUFFIX] chars so the
     * result always fits the `news_fetch.ticker` / `news_article_ticker.ticker` VARCHAR(32) columns
     * regardless of how long an arbitrary `/news/topic` input is. Truncation is lossy — see
     * [topicTagFor] for why the upstream EODHD call must NOT be derived from this key.
     */
    private fun topicKey(topic: String): String =
        TOPIC_PREFIX +
            topic
                .trim()
                .uppercase()
                .replace(" ", "_")
                .take(TOPIC_KEY_MAX_SUFFIX)

    /**
     * The EODHD tag for topic key [key]. Prefers the original, untruncated topic carried in
     * [topicTags] (populated by [getTopicNews] for every key it mints) — [topicKey] truncates to
     * fit the VARCHAR(32) ticker columns, so reconstructing the tag FROM the (possibly truncated)
     * key would silently send EODHD a corrupted tag for any topic longer than
     * [TOPIC_KEY_MAX_SUFFIX] characters. The key-derived fallback only exists for callers that
     * don't have (or need) the original — it's exact for any key that was never truncated.
     */
    private fun topicTagFor(
        key: String,
        topicTags: Map<String, String> = emptyMap()
    ): String = topicTags[key]?.lowercase() ?: key.removePrefix(TOPIC_PREFIX).lowercase().replace("_", " ")

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
        val embeddings = computeEmbeddings(fresh)
        for (incoming in fresh) {
            val externalId = incoming.link.ifBlank { "${incoming.date}|${incoming.title}" }
            saveOrMerge(symbol, externalId, incoming, embeddings[externalId])
        }
    }

    /**
     * Batch-embeds the [fresh] articles that actually need a (re)computed vector, skipping any
     * whose stored row already carries an embedding for the current [NewsEmbedder.modelId] with
     * an unchanged title — EODHD republishes the same article on every poll, so without this every
     * refresh would re-embed its whole feed. A no-op entirely when [newsEmbedder] is inactive (the
     * default), so the noop path never touches the repo for this.
     *
     * Runs before the (still serial, calling-thread) persist loop in [upsertAll]. The embed call
     * is network I/O against `bc-embed`, but it only produces plain [FloatArray] values here — no
     * entity or Hibernate session is touched off-thread.
     */
    private fun computeEmbeddings(fresh: List<EodhdNewsArticle>): Map<String, ArticleEmbedding> {
        if (!newsEmbedder.active || fresh.isEmpty()) return emptyMap()

        val candidates =
            fresh.map { incoming ->
                val externalId = incoming.link.ifBlank { "${incoming.date}|${incoming.title}" }
                Candidate(externalId, incoming, newsArticleRepo.findByExternalId(externalId).orElse(null))
            }
        val toEmbed = candidates.filterNot { isEmbeddingCurrent(it.existing, it.incoming.title) }
        if (toEmbed.isEmpty()) return emptyMap()

        val texts = toEmbed.map { embeddingText(it.incoming, it.existing?.summary) }
        val vectors = newsEmbedder.embed(texts)
        if (vectors.size != texts.size) {
            // Partial-batch failure (or a provider that doesn't honour request order/size).
            // Discarding the whole round is deliberate — a positional zip here would silently pair
            // the wrong vector with the wrong article, which is worse than skipping embedding
            // until the next refresh.
            log.warn(
                "news_embedding: {} returned {} vector(s) for {} text(s) — skipping this batch",
                newsEmbedder.modelId,
                vectors.size,
                texts.size
            )
            return emptyMap()
        }

        return toEmbed
            .zip(vectors) { candidate, vector ->
                candidate.externalId to ArticleEmbedding(vector, newsEmbedder.modelId)
            }.toMap()
    }

    private fun isEmbeddingCurrent(
        existing: NewsArticle?,
        incomingTitle: String
    ): Boolean =
        existing != null &&
            existing.embedding != null &&
            existing.embeddingModel == newsEmbedder.modelId &&
            existing.title == incomingTitle

    private fun embeddingText(
        incoming: EodhdNewsArticle,
        existingSummary: String?
    ): String {
        val tail = existingSummary?.takeIf { it.isNotBlank() } ?: incoming.content
        return "${incoming.title}. $tail".take(EMBEDDING_TEXT_MAX_CHARS)
    }

    private data class Candidate(
        val externalId: String,
        val incoming: EodhdNewsArticle,
        val existing: NewsArticle?
    )

    private class ArticleEmbedding(
        val vector: FloatArray,
        val model: String
    )

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
        incoming: EodhdNewsArticle,
        embedding: ArticleEmbedding?
    ) {
        val existing = newsArticleRepo.findByExternalId(externalId).orElse(null)
        val article =
            existing ?: NewsArticle(
                externalId = externalId,
                // Placeholder — applyIncoming() overwrites both stamps immediately below.
                published = LocalDateTime.now(dateUtils.zoneId),
                fetchedAt = LocalDateTime.now(dateUtils.zoneId)
            )
        applyIncoming(article, externalId, symbol, incoming, embedding)
        try {
            newsArticleRepo.save(article)
        } catch (e: DataIntegrityViolationException) {
            val winner = newsArticleRepo.findByExternalId(externalId).orElseThrow { e }
            applyIncoming(winner, externalId, symbol, incoming, embedding)
            newsArticleRepo.save(winner)
        }
    }

    private fun applyIncoming(
        article: NewsArticle,
        externalId: String,
        symbol: String,
        incoming: EodhdNewsArticle,
        embedding: ArticleEmbedding?
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

        // Only present when computeEmbeddings() actually (re)computed a vector this round — a
        // skip-recompute hit (title + model unchanged) leaves the article's existing
        // embedding/embeddingModel/derivedTopics untouched.
        if (embedding != null) {
            article.embedding = EmbeddingCodec.encode(embedding.vector)
            article.embeddingModel = embedding.model
            article.derivedTopics.clear()
            article.derivedTopics.addAll(
                topicAnchorStore.matchingTopics(embedding.vector, embeddingProperties.topicThreshold)
            )
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
        // EODHD's own tags first (exact vendor match), then BC's anchor-derived topics — closes
        // the coverage gap when EODHD ships no tags for an article at all.
        return article.tags.any { it.uppercase() == needle } ||
            article.derivedTopics.any { it.uppercase() == needle }
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

        // Cap on the "title. summary-or-content" text sent to the embedder per article — the
        // model truncates internally anyway, and this keeps the request payload small.
        private const val EMBEDDING_TEXT_MAX_CHARS = 512
        private const val BULLISH_THRESHOLD = 0.35
        private const val BEARISH_THRESHOLD = -0.35
        private val TICKER_PATTERN = Regex("[A-Z0-9.-]{1,10}")

        // Synthetic key prefix distinguishing a topic-tag query from a ticker/index symbol in the
        // shared refresh/rank pipeline. See [topicKey] / [topicTagFor].
        private const val TOPIC_PREFIX = "TOPIC:"

        // news_fetch.ticker / news_article_ticker.ticker are VARCHAR(32). TOPIC_PREFIX is 6 chars,
        // so the normalized topic suffix gets 32 - 6 = 26.
        private const val TOPIC_KEY_MAX_SUFFIX = 26
    }
}