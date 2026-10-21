package com.beancounter.agent.review

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface AssetReviewRepository : JpaRepository<AssetReview, String> {
    fun findByMarketAndTickerAndReviewDateAndModel(
        market: String,
        ticker: String,
        reviewDate: LocalDate,
        model: String
    ): AssetReview?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from AssetReview r where r.reviewDate < :cutoff")
    fun deleteReviewedBefore(cutoff: LocalDate): Int
}