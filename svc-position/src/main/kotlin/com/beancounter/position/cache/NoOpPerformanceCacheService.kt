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

    /**
     * Does nothing when requested to invalidate cache entries for the given date.
     *
     * @param date The date for which cache invalidation was requested.
     */
    override fun invalidateOnDate(date: LocalDate) {
        // No-op
    }

    /**
     * No-op invalidation for entries from the given date.
     *
     * This implementation ignores the provided date and performs no action.
     *
     * @param fromDate The date from which cache entries would be invalidated (ignored).
     */
    override fun invalidateFromDate(fromDate: LocalDate) {
        // No-op
    }

    /**
     * No-op invalidation for a portfolio's cached snapshots.
     *
     * This implementation performs no action and does not modify any cache state.
     *
     * @param portfolioId The identifier of the portfolio whose cached snapshots would be invalidated.
     */
    override fun invalidatePortfolio(portfolioId: String) {
        // No-op
    }

    override fun isAvailable(): Boolean = false
}