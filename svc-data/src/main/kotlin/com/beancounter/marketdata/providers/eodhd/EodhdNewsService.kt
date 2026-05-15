package com.beancounter.marketdata.providers.eodhd

import com.beancounter.marketdata.providers.NewsProvider
import com.beancounter.marketdata.providers.eodhd.model.EodhdNewsArticle
import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.abs

/**
 * EODHD `/api/news` adapter, projecting the EODHD response into the AV-compatible `{feed, count}`
 * shape that svc-agent's NewsTools already consumes.
 *
 * Each ticker is queried individually because EODHD's news endpoint is single-symbol. The merged
 * results are ranked by `|sentiment.polarity|` (strongest opinion first) and truncated to
 * [EodhdNewsProperties.maxArticles] so the LLM tool_result stays compact.
 *
 * EODHD's response carries a richer per-article sentiment than AV (polarity + pos/neg/neu split).
 * The projection keeps both the AV-style `sentimentLabel` / `sentimentScore` keys so existing
 * consumers don't change shape, and adds `polarity` + `tags` + `symbols` as bonus fields the
 * LLM can use for context.
 */
@Service
class EodhdNewsService(
    private val eodhdProxy: EodhdProxy,
    private val eodhdConfig: EodhdConfig,
    private val newsProperties: EodhdNewsProperties
) : NewsProvider {
    private val log = LoggerFactory.getLogger(EodhdNewsService::class.java)

    @Cacheable("eodhd.news.sentiment", key = "#tickers + '-' + (#market ?: '') + '-' + (#topics ?: '')")
    override fun getNewsSentiment(
        tickers: String,
        market: String?,
        topics: String?
    ): Map<String, Any> {
        val symbols = resolveSymbols(tickers, market)
        if (symbols.isEmpty()) return emptyMap()

        val all = mutableListOf<EodhdNewsArticle>()
        for (symbol in symbols) {
            try {
                all.addAll(
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
            }
        }
        if (all.isEmpty()) return emptyMap()

        val ranked =
            all
                .asSequence()
                .filter { topics.isNullOrBlank() || matchesTopic(it, topics) }
                .sortedByDescending { abs(polarityOf(it)) }
                .take(newsProperties.maxArticles)
                .map { project(it) }
                .toList()

        if (ranked.isEmpty()) return emptyMap()
        return mapOf(
            "feed" to ranked,
            "count" to ranked.size
        )
    }

    /**
     * Compose `${ticker}.${exchange}` per EODHD's convention.
     * - When a market is supplied, look up its `eodhd:` alias (US, LSE, AU, …).
     * - When omitted, default to the `US` exchange — EODHD's convention for NYSE/NASDAQ/AMEX equities.
     */
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

    private fun polarityOf(article: EodhdNewsArticle): Double = article.sentiment?.polarity?.toDouble() ?: 0.0

    private fun matchesTopic(
        article: EodhdNewsArticle,
        topic: String
    ): Boolean {
        val needle = topic.trim().uppercase()
        return article.tags.any { it.uppercase() == needle }
    }

    private fun project(article: EodhdNewsArticle): Map<String, Any> {
        val polarity = polarityOf(article)
        return mapOf(
            "title" to article.title,
            "summary" to article.content.take(SUMMARY_CHARS),
            "source" to article.link,
            "timePublished" to article.date,
            "sentimentLabel" to labelFor(polarity),
            "sentimentScore" to scaledScore(polarity),
            "relevance" to 1.0,
            "tickerSentimentLabel" to labelFor(polarity),
            "tickerSentimentScore" to scaledScore(polarity),
            "polarity" to polarity,
            "symbols" to article.symbols,
            "tags" to article.tags
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
        // Cap the article body fed back to the LLM so tool_result payloads stay small.
        private const val SUMMARY_CHARS = 400

        // EODHD polarity is roughly [-1, 1]. Bands align with AV's Bullish/Neutral/Bearish vocabulary.
        private const val BULLISH_THRESHOLD = 0.35
        private const val BEARISH_THRESHOLD = -0.35

        private val TICKER_PATTERN = Regex("[A-Z0-9.-]{1,10}")
    }
}