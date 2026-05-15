package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.NewsProvider
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import com.beancounter.marketdata.providers.eodhd.news.NewsArticle
import com.beancounter.marketdata.providers.eodhd.news.NewsArticleRepo
import com.beancounter.marketdata.providers.eodhd.news.NewsArticleTicker
import com.beancounter.marketdata.providers.eodhd.news.NewsFetch
import com.beancounter.marketdata.providers.eodhd.news.NewsFetchRepo
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
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
    private val newsFetchRepo: NewsFetchRepo
) : NewsProvider {
    private val log = LoggerFactory.getLogger(EodhdNewsService::class.java)

    @Transactional
    override fun getNewsSentiment(
        tickers: String,
        market: String?,
        topics: String?
    ): Map<String, Any> {
        val symbols = resolveSymbols(tickers, market)
        if (symbols.isEmpty()) return emptyMap()

        val refreshCutoff = LocalDateTime.now().minusHours(newsProperties.refreshAfterHours)
        for (symbol in symbols) {
            if (shouldRefresh(symbol, refreshCutoff)) {
                refreshFromUpstream(symbol)
            }
        }

        val retentionStart = LocalDateTime.now().minusDays(newsProperties.retentionDays)
        val stored = newsArticleRepo.findByTickersAfter(symbols, retentionStart)
        val ranked =
            stored
                .asSequence()
                .filter { topics.isNullOrBlank() || matchesTopic(it, topics) }
                .sortedByDescending { abs(it.polarity.toDouble()) }
                .take(newsProperties.maxArticles)
                .map { project(it) }
                .toList()

        if (ranked.isEmpty()) return emptyMap()
        return mapOf(
            "feed" to ranked,
            "count" to ranked.size
        )
    }

    private fun shouldRefresh(
        symbol: String,
        cutoff: LocalDateTime
    ): Boolean {
        val meta = newsFetchRepo.findById(symbol).orElse(null)
        return meta == null || meta.lastFetchedAt.isBefore(cutoff)
    }

    private fun refreshFromUpstream(symbol: String) {
        try {
            val fresh =
                eodhdProxy.getNews(
                    symbol = symbol,
                    limit = newsProperties.providerLimit,
                    from = null,
                    apiKey = eodhdConfig.apiKey
                )
            upsertAll(symbol, fresh)
            newsFetchRepo.save(NewsFetch(symbol, LocalDateTime.now(), fresh.size))
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.debug("EODHD news lookup failed for {}: {}", symbol, e.message)
            // Don't poison the next refresh — keep the existing news_fetch row so the stale-cutoff
            // logic falls back to whatever the DB has without thrashing on a broken upstream.
        }
    }

    private fun upsertAll(
        symbol: String,
        fresh: List<EodhdNewsArticle>
    ) {
        for (incoming in fresh) {
            val externalId = incoming.link.ifBlank { "${incoming.date}|${incoming.title}" }
            val existing = newsArticleRepo.findByExternalId(externalId).orElse(null)
            val article = existing ?: NewsArticle(externalId = externalId)
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
            article.fetchedAt = LocalDateTime.now()

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

            newsArticleRepo.save(article)
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
                try {
                    eodhdConfig.marketService.getMarket(market).getAlias(EodhdPriceService.ID) ?: market
                } catch (
                    @Suppress("TooGenericExceptionCaught")
                    e: Exception
                ) {
                    log.debug("No EODHD alias for market {}: {}", market, e.message)
                    market
                }
            }
        return tickers
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.matches(TICKER_PATTERN) }
            .map { "$it.$exchange" }
    }

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
            "summary" to article.content.take(SUMMARY_CHARS),
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
        private const val SUMMARY_CHARS = 400
        private const val BULLISH_THRESHOLD = 0.35
        private const val BEARISH_THRESHOLD = -0.35
        private val TICKER_PATTERN = Regex("[A-Z0-9.-]{1,10}")
    }
}