package com.beancounter.marketdata.news.sentiment

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.news.NewsArticleRepo
import com.beancounter.marketdata.news.NewsServiceFacade
import com.beancounter.marketdata.news.ScopeDayCount
import com.beancounter.marketdata.news.eodhd.EodhdNewsProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime

/** One day of a market sentiment series, as served by `GET /news/sentiment/market`. */
data class MarketSentimentPoint(
    val date: LocalDate,
    val positive: Int,
    val neutral: Int,
    val negative: Int,
    val polarity: BigDecimal
)

/** The daily sentiment of one macro topic or one sector, oldest day first. */
data class MarketSentimentSeries(
    val kind: SentimentScopeKind,
    val name: String,
    val points: List<MarketSentimentPoint>
)

/** Counts from one [MarketSentimentService.refresh] run. */
data class MarketSentimentRefreshResult(
    val scopes: Int,
    val rows: Int
)

/**
 * Daily Positive / Neutral / Negative news flow per market scope — each macro topic and each
 * sector keeps its own series, so a bad day for energy never hides inside a good day for the
 * market.
 *
 * [refresh] downloads the news for every scope in [MarketSentimentScopes] (the news pipeline skips
 * a scope fetched inside its refresh window), then [rollup] counts the stored articles per scope
 * and day, classified by [SentimentBands]. An article filed under two scopes counts in both.
 *
 * [rollup] recounts the whole stored window on every run rather than only new days: articles
 * arrive late, and one grouped query is cheap. The window stops [BOUNDARY_DAYS] short of the
 * article retention period, because the oldest stored day is partly pruned and recounting it would
 * overwrite a complete count with a partial one. A row is only rewritten when its counts moved.
 *
 * [get] is a database read and never calls a news provider.
 */
@Service
class MarketSentimentService(
    private val newsServiceFacade: NewsServiceFacade,
    private val newsArticleRepo: NewsArticleRepo,
    private val repo: MarketSentimentDailyRepository,
    private val newsProperties: EodhdNewsProperties,
    private val dateUtils: DateUtils = DateUtils()
) {
    fun refresh(): MarketSentimentRefreshResult {
        runCatching { newsServiceFacade.getTopicNews(MarketSentimentScopes.TOPICS) }
            .onFailure { log.warn("Topic news download failed", it) }
        runCatching { newsServiceFacade.getMarketNews(MarketSentimentScopes.SECTORS.values.toList(), null) }
            .onFailure { log.warn("Sector news download failed", it) }
        val result = MarketSentimentRefreshResult(MarketSentimentScopes.ALL.size, rollup())
        log.info("Market sentiment refresh: {}", result)
        return result
    }

    /** Recounts the stored article window into daily rows. Returns the number of rows written. */
    fun rollup(): Int {
        val from = dateUtils.date.minusDays(newsProperties.retentionDays - BOUNDARY_DAYS)
        val scopes = MarketSentimentScopes.ALL.associateBy { it.key }
        val existing =
            repo
                .findByNewsDateGreaterThanEqualOrderByNewsDateAsc(from)
                .associateBy { RowKey(it.scopeKind, it.scopeName, it.newsDate) }
        val updatedAt = LocalDateTime.now(dateUtils.zoneId)
        val toSave =
            newsArticleRepo
                .countByScopeAndDay(scopes.keys, from.atStartOfDay(), SentimentBands.POSITIVE, SentimentBands.NEGATIVE)
                .mapNotNull { count ->
                    val scope = scopes.getValue(count.scopeKey)
                    merge(existing[RowKey(scope.kind, scope.name, count.newsDate)], scope, count, updatedAt)
                }
        repo.saveAll(toSave)
        return toSave.size
    }

    /** Every scope's series for the trailing [days], topics first. Scopes with no rows are absent. */
    fun get(days: Int): List<MarketSentimentSeries> =
        repo
            .findByNewsDateGreaterThanEqualOrderByNewsDateAsc(dateUtils.date.minusDays(days.toLong()))
            .groupBy { it.scopeKind to it.scopeName }
            .map { (scope, rows) ->
                MarketSentimentSeries(
                    scope.first,
                    scope.second,
                    rows.map { MarketSentimentPoint(it.newsDate, it.positive, it.neutral, it.negative, it.polarity) }
                )
            }.sortedWith(compareBy({ it.kind }, { it.name }))

    /** The row to save for [count], or null when the stored [row] already matches it. */
    private fun merge(
        row: MarketSentimentDaily?,
        scope: MarketSentimentScope,
        count: ScopeDayCount,
        updatedAt: LocalDateTime
    ): MarketSentimentDaily? {
        val positive = count.positive.toInt()
        val negative = count.negative.toInt()
        val neutral = count.total.toInt() - positive - negative
        val polarity = BigDecimal.valueOf(count.polarity).setScale(POLARITY_SCALE, RoundingMode.HALF_UP)
        val target = row ?: MarketSentimentDaily(scope.kind, scope.name, count.newsDate)
        val unchanged =
            row != null &&
                row.positive == positive &&
                row.neutral == neutral &&
                row.negative == negative &&
                row.polarity.compareTo(polarity) == 0
        if (unchanged) {
            return null
        }
        target.positive = positive
        target.neutral = neutral
        target.negative = negative
        target.polarity = polarity
        target.updatedAt = updatedAt
        return target
    }

    private data class RowKey(
        val kind: SentimentScopeKind,
        val name: String,
        val date: LocalDate
    )

    companion object {
        private val log = LoggerFactory.getLogger(MarketSentimentService::class.java)

        /** Days at the old end of the article retention window left out of a recount. */
        const val BOUNDARY_DAYS = 2L

        // Scale of `news_market_sentiment_daily.polarity` (`NUMERIC(6,4)`), so a recount compares
        // equal to the stored value.
        private const val POLARITY_SCALE = 4
    }
}