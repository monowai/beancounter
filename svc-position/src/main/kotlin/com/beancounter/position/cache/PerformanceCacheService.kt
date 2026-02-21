package com.beancounter.position.cache

import java.math.BigDecimal
import java.time.LocalDate

data class CachedSnapshot(
    val valuationDate: LocalDate,
    val marketValue: BigDecimal,
    val externalCashFlow: BigDecimal,
    val netContributions: BigDecimal,
    val cumulativeDividends: BigDecimal
)

interface PerformanceCacheService {
    fun findAllSnapshots(portfolioId: String): List<CachedSnapshot>

    fun findSnapshots(
        portfolioId: String,
        dates: List<LocalDate>
    ): List<CachedSnapshot>

    fun storeSnapshots(
        portfolioId: String,
        snapshots: List<CachedSnapshot>
    )

    fun invalidateFrom(
        portfolioId: String,
        fromDate: LocalDate
    )

    fun invalidateOnDate(date: LocalDate)

    fun invalidatePortfolio(portfolioId: String)

    fun isAvailable(): Boolean
}