package com.beancounter.position.cache

import java.time.LocalDate

/**
 * No-op cache implementation for use in tests that don't need database caching.
 * Not a Spring bean — instantiate directly when needed.
 */
class NoOpPerformanceCacheService : PerformanceCacheService {
    override fun findAllSnapshots(portfolioId: String): List<CachedSnapshot> = emptyList()

    override fun findSnapshots(
        portfolioId: String,
        dates: List<LocalDate>
    ): List<CachedSnapshot> = emptyList()

    override fun storeSnapshots(
        portfolioId: String,
        snapshots: List<CachedSnapshot>
    ) {
        // No-op
    }

    override fun invalidateFrom(
        portfolioId: String,
        fromDate: LocalDate
    ) {
        // No-op
    }

    override fun invalidateOnDate(date: LocalDate) {
        // No-op
    }

    override fun invalidatePortfolio(portfolioId: String) {
        // No-op
    }

    override fun isAvailable(): Boolean = false
}