package com.beancounter.marketdata.providers.eodhd.news

import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime
import java.util.Optional

/**
 * CRUD + window finders for [NewsArticle]. The two key surfaces:
 *  - [findByTickersAfter] — read path: pull articles for one or more tickers published inside the
 *    retention window (`since = now - retention-days`).
 *  - [deleteByPublishedBefore] — retention sweep, called from [NewsRetentionSchedule].
 */
interface NewsArticleRepo : CrudRepository<NewsArticle, String> {
    fun findByExternalId(externalId: String): Optional<NewsArticle>

    @Query(
        "SELECT DISTINCT a FROM NewsArticle a JOIN a.tickerLinks t " +
            "WHERE t.ticker IN :tickers AND a.published >= :since " +
            "ORDER BY a.published DESC"
    )
    fun findByTickersAfter(
        @Param("tickers") tickers: Collection<String>,
        @Param("since") since: LocalDateTime
    ): List<NewsArticle>

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