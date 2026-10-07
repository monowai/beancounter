package com.beancounter.marketdata.news

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

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

    /** Most recent stored day for one asset; null when the asset has never been refreshed. */
    fun findTopByAssetIdOrderByPriceDateDesc(assetId: String): NewsSentimentDaily?
}