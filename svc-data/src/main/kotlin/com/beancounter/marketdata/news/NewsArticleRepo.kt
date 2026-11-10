package com.beancounter.marketdata.news

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.Optional

/** Interface projection for [NewsArticleRepo.countByScopeAndDay]: one key's articles on one day. */
interface ScopeDayCount {
    val scopeKey: String
    val newsDate: LocalDate
    val positive: Long
    val negative: Long
    val total: Long
    val polarity: Double
}

/**
 * CRUD + window finders for [NewsArticle]. The key surfaces:
 *  - [findByTickersAfter] — read path: pull articles for one or more tickers published inside the
 *    retention window (`since = now - retention-days`).
 *  - [countByScopeAndDay] — sentiment rollup: grouped article counts per key and day.
 *  - [deleteByPublishedBefore] — retention sweep, called from [NewsRetentionSchedule].
 */
interface NewsArticleRepo : CrudRepository<NewsArticle, String> {
    fun findByExternalId(externalId: String): Optional<NewsArticle>

    /** Ingest preload: every stored row for one fetch's external ids, in a single query. */
    fun findAllByExternalIdIn(externalIds: Collection<String>): List<NewsArticle>

    @Query(
        "SELECT DISTINCT a FROM NewsArticle a JOIN a.tickerLinks t " +
            "WHERE t.ticker IN :tickers AND a.published >= :since " +
            "ORDER BY a.published DESC"
    )
    fun findByTickersAfter(
        @Param("tickers") tickers: Collection<String>,
        @Param("since") since: LocalDateTime
    ): List<NewsArticle>

    /**
     * Article counts per key and publication day for [keys], on or after [since]: how many sit at
     * or above [positive], at or below [negative], the total, and the mean polarity. One grouped
     * query, so no article bodies are loaded.
     */
    @Query(
        "SELECT t.ticker AS scopeKey, CAST(a.published AS LocalDate) AS newsDate, " +
            "SUM(CASE WHEN a.polarity >= :positive THEN 1 ELSE 0 END) AS positive, " +
            "SUM(CASE WHEN a.polarity <= :negative THEN 1 ELSE 0 END) AS negative, " +
            "COUNT(a) AS total, AVG(a.polarity) AS polarity " +
            "FROM NewsArticle a JOIN a.tickerLinks t " +
            "WHERE t.ticker IN :keys AND a.published >= :since " +
            "GROUP BY t.ticker, CAST(a.published AS LocalDate)"
    )
    fun countByScopeAndDay(
        @Param("keys") keys: Collection<String>,
        @Param("since") since: LocalDateTime,
        @Param("positive") positive: BigDecimal,
        @Param("negative") negative: BigDecimal
    ): List<ScopeDayCount>

    @Modifying
    @Query("DELETE FROM NewsArticle a WHERE a.published < :threshold")
    fun deleteByPublishedBefore(
        @Param("threshold") threshold: LocalDateTime
    ): Int

    /**
     * Group news article count by [NewsArticle.source]. Surfaced as the
     * `beancounter.news.count.by_source` MultiGauge.
     */
    @Query(
        "SELECT new com.beancounter.marketdata.metrics.SourceCount(a.source, COUNT(a)) " +
            "FROM NewsArticle a GROUP BY a.source ORDER BY COUNT(a) DESC"
    )
    fun countBySource(): List<com.beancounter.marketdata.metrics.SourceCount>
}