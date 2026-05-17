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

    /**
 * Invalidates all cached performance snapshots associated with the given valuation date across all portfolios.
 *
 * @param date The valuation date whose cached snapshots should be invalidated.
 */
fun invalidateOnDate(date: LocalDate)

    /**
 * Invalidates cached performance snapshots for all portfolios starting from the specified date.
 *
 * @param fromDate The date (inclusive) from which cached entries should be invalidated across all portfolios.
 */
fun invalidateFromDate(fromDate: LocalDate)

    /**
 * Invalidates all cached performance snapshots for the specified portfolio.
 *
 * @param portfolioId The unique identifier of the portfolio whose cached snapshots should be removed.
 */
fun invalidatePortfolio(portfolioId: String)

    /**
 * Indicates whether the performance cache service can be used.
 *
 * @return `true` if the cache service is available, `false` otherwise.
 */
fun isAvailable(): Boolean
}