package com.beancounter.marketdata.news

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

/** Interface projection for [NewsSentimentDailyRepository.findLatestPriceDates]. */
interface LatestSentimentDate {
    val assetId: String
    val priceDate: LocalDate
}

/**
 * Read / upsert surface for [NewsSentimentDaily]. No `@Query` needed — both finders derive from
 * the `(asset_id, price_date)` unique index.
 */
interface NewsSentimentDailyRepository : JpaRepository<NewsSentimentDaily, String> {
    /** Points for [assetIds] on or after [from], oldest first — the `GET /news/sentiment` read. */
    fun findByAssetIdInAndPriceDateGreaterThanEqualOrderByPriceDateAsc(
        assetIds: Collection<String>,
        from: LocalDate
    ): List<NewsSentimentDaily>

    /**
     * Most recent stored day per asset, one query for the whole batch. Assets that have never
     * been refreshed are simply missing from the result.
     */
    @Query(
        "SELECT s.assetId AS assetId, MAX(s.priceDate) AS priceDate FROM NewsSentimentDaily s " +
            "WHERE s.assetId IN :assetIds GROUP BY s.assetId"
    )
    fun findLatestPriceDates(
        @Param("assetIds") assetIds: Collection<String>
    ): List<LatestSentimentDate>
}