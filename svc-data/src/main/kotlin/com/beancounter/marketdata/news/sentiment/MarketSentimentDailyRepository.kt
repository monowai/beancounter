package com.beancounter.marketdata.news.sentiment

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

/** Read / upsert surface for [MarketSentimentDaily]. */
interface MarketSentimentDailyRepository : JpaRepository<MarketSentimentDaily, String> {
    /** Every scope's rows on or after [from], oldest first. */
    fun findByNewsDateGreaterThanEqualOrderByNewsDateAsc(from: LocalDate): List<MarketSentimentDaily>
}