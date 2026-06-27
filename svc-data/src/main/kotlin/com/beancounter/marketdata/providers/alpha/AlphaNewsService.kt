package com.beancounter.marketdata.providers.alpha

import com.beancounter.marketdata.providers.NewsProvider
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs

/**
 * Fetches news and sentiment data from the Alpha Vantage NEWS_SENTIMENT API,
 * then trims the response to only the highest-impact articles for the
 * requested tickers.
 *
 * The raw provider feed can include 50+ articles with per-article topic
 * lists, banner images, and sentiment records for every ticker mentioned —
 * easily 20k+ tokens when fed back into an LLM as a tool_result. This
 * service applies a recency window (business days), relevance filter, and
 * top-N ranking before projecting onto a compact shape.
 *
 * Ranking: articles are sorted by the requested ticker's `relevance_score`
 * descending, with ties broken by `|ticker_sentiment_score|` (strongest
 * opinion first).
 */
@Service
class AlphaNewsService(
    private val alphaGateway: AlphaGateway,
    private val alphaConfig: AlphaConfig,
    private val objectMapper: ObjectMapper,
    private val newsProperties: NewsProperties
) : NewsProvider {
    private val log = LoggerFactory.getLogger(AlphaNewsService::class.java)

    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    // Pipe + null-sentinel separator so the cache doesn't collide on legitimate values that
    // contain dashes (e.g. tickers like `BRK-B`).
    @Cacheable("news.sentiment", key = "#tickers + '|' + (#market ?: '~') + '|' + (#topics ?: '~')")
    override fun getNewsSentiment(
        tickers: String,
        market: String?,
        topics: String?
    ): Map<String, Any> {
        // NEWS_SENTIMENT only covers US-listed equities reliably.
        // Non-US markets return US tickers with the same symbol, so
        // short-circuit to avoid returning misleading data.
        if (!market.isNullOrBlank() && !alphaConfig.isNullMarket(market)) {
            return emptyMap()
        }
        val validTickers = sanitiseTickers(tickers)
        if (validTickers.isEmpty()) return emptyMap()

        val result = fetchAndTrim(validTickers.joinToString(","), topics)
        if (result.isNotEmpty()) return result

        // AV returns empty for the entire batch when any ticker is
        // unrecognised. Fall back to querying each ticker individually
        // and merge the ranked results.
        if (validTickers.size > 1) {
            log.debug("Batch returned empty — falling back to per-ticker queries for {}", validTickers)
            return fetchPerTicker(validTickers, topics)
        }
        return emptyMap()
    }

    /**
     * Strip values that aren't plausible ticker symbols. AV rejects the
     * entire request if any ticker is invalid, so one bad value (e.g. a
     * company name the LLM slipped in) poisons the whole batch.
     */
    private fun sanitiseTickers(tickers: String): List<String> =
        tickers
            .split(",")
            .map { it.trim().uppercase() }
            .filter { it.length in 1..6 && it.matches(TICKER_PATTERN) }

    private fun fetchAndTrim(
        tickers: String,
        topics: String?
    ): Map<String, Any> {
        val timeFrom =
            businessDaysAgo(newsProperties.businessDaysBack)
                .atTime(LocalTime.MIDNIGHT)
                .format(AV_TIME_FORMAT)
        val json =
            alphaGateway.getNewsSentiment(
                tickers = tickers,
                apiKey = apiKey,
                topics = topics,
                limit = newsProperties.providerLimit,
                timeFrom = timeFrom
            )
        if (json.isBlank() || json.contains("Error Message")) {
            return emptyMap()
        }
        val raw = parse(json) ?: return emptyMap()
        return trim(raw, tickerSet(tickers))
    }

    private fun fetchPerTicker(
        tickers: List<String>,
        topics: String?
    ): Map<String, Any> {
        val allArticles = mutableListOf<Map<String, Any>>()
        for (ticker in tickers) {
            val result = fetchAndTrim(ticker, topics)

            @Suppress("UNCHECKED_CAST")
            val feed = result["feed"] as? List<Map<String, Any>>
            if (feed != null) allArticles.addAll(feed)
        }
        if (allArticles.isEmpty()) return emptyMap()

        // Re-rank the merged set and take the top N.
        val ranked =
            allArticles
                .sortedWith(
                    compareByDescending<Map<String, Any>> {
                        (it["relevance"] as? Number)?.toDouble() ?: 0.0
                    }.thenByDescending {
                        abs((it["tickerSentimentScore"] as? Number)?.toDouble() ?: 0.0)
                    }
                ).take(newsProperties.maxArticles)

        return mapOf("feed" to ranked, "count" to ranked.size)
    }

    private fun parse(json: String): Map<String, Any>? =
        try {
            @Suppress("UNCHECKED_CAST")
            objectMapper.readValue(json, Map::class.java) as Map<String, Any>
        } catch (
            // Provider JSON is untrusted; malformed payload or shape mismatch → drop the feed.
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Failed to parse news feed: {}", e.message)
            null
        }

    /**
     * Rank articles for the requested tickers and project each to a
     * minimal shape. Articles without sufficient relevance for any of
     * the requested tickers are dropped.
     */
    private fun trim(
        raw: Map<String, Any>,
        tickers: Set<String>
    ): Map<String, Any> {
        @Suppress("UNCHECKED_CAST")
        val feed = raw["feed"] as? List<Map<String, Any>> ?: return emptyMap()

        val ranked =
            feed
                .mapNotNull { article -> scoreArticle(article, tickers) }
                .filter { it.relevance >= newsProperties.relevanceThreshold }
                .sortedWith(
                    compareByDescending<Scored> { it.relevance }
                        .thenByDescending { abs(it.sentimentMagnitude) }
                ).take(newsProperties.maxArticles)
                .map { it.projected }

        if (ranked.isEmpty()) return emptyMap()

        return mapOf(
            "feed" to ranked,
            "count" to ranked.size
        )
    }

    private fun scoreArticle(
        article: Map<String, Any>,
        tickers: Set<String>
    ): Scored? {
        @Suppress("UNCHECKED_CAST")
        val tickerSentiments = article["ticker_sentiment"] as? List<Map<String, Any>> ?: return null
        val matched = tickerSentiments.filter { (it["ticker"] as? String)?.uppercase() in tickers }
        if (matched.isEmpty()) return null

        // Use the best-matched ticker as the article's representative score.
        val best =
            matched.maxBy {
                (it["relevance_score"] as? String)?.toDoubleOrNull() ?: 0.0
            }
        val relevance = (best["relevance_score"] as? String)?.toDoubleOrNull() ?: 0.0
        val sentimentScore = (best["ticker_sentiment_score"] as? String)?.toDoubleOrNull() ?: 0.0

        val projected =
            mapOf(
                "title" to (article["title"] ?: ""),
                "summary" to (article["summary"] ?: ""),
                "source" to (article["source"] ?: ""),
                "timePublished" to (article["time_published"] ?: ""),
                "sentimentLabel" to (article["overall_sentiment_label"] ?: ""),
                "sentimentScore" to
                    ((article["overall_sentiment_score"] as? Number)?.toDouble() ?: 0.0),
                "relevance" to relevance,
                "tickerSentimentLabel" to (best["ticker_sentiment_label"] ?: ""),
                "tickerSentimentScore" to sentimentScore
            )
        return Scored(
            relevance = relevance,
            sentimentMagnitude = sentimentScore,
            projected = projected
        )
    }

    private fun tickerSet(tickers: String): Set<String> =
        tickers
            .split(",")
            .map { it.trim().substringBefore(".").uppercase() }
            .filter { it.isNotBlank() }
            .toSet()

    private fun businessDaysAgo(days: Int): LocalDate {
        var date = LocalDate.now()
        var remaining = days
        while (remaining > 0) {
            date = date.minusDays(1)
            if (date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY) {
                remaining--
            }
        }
        return date
    }

    private data class Scored(
        val relevance: Double,
        val sentimentMagnitude: Double,
        val projected: Map<String, Any>
    )

    companion object {
        // AV's time_from format is YYYYMMDDTHHMM, no colon.
        private val AV_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")

        // Plausible ticker: 1–6 uppercase alphanumeric chars, optionally with a dot suffix.
        private val TICKER_PATTERN = Regex("[A-Z0-9]+(\\.[A-Z0-9]+)?")
    }
}