package com.beancounter.position.cache

import com.beancounter.common.model.Portfolio
import org.slf4j.LoggerFactory
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A portfolio's performance snapshots as last computed, served only while they still
 * cover the requested window. A cache failure never fails a performance request.
 */
@Component
class PerformanceSnapshotCache(
    private val cacheService: PerformanceCacheService
) {
    /**
     * Cached snapshots for `[startDate, endDate]`, anchored at [startDate], or null
     * when the cache is missing, short or stale.
     */
    fun window(
        portfolio: Portfolio,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<CachedSnapshot>? {
        val cached = load(portfolio.id) ?: return null
        val earliestCached = cached.minOf { it.valuationDate }
        val latestCached = cached.maxOf { it.valuationDate }
        val hasInWindow = cached.any { !it.valuationDate.isBefore(startDate) }
        // `earliestCached <= startDate` alone is not enough: if every cached
        // snapshot predates startDate, `anchorToStartDate` returns just the
        // synthesised anchor (single-point series), producing 0% TWR and 0
        // gain. Require at least one snapshot in `[startDate, endDate]`.
        val hasSufficientHistory = !earliestCached.isAfter(startDate) && hasInWindow
        // RabbitMQ invalidation (CacheInvalidationConsumer.invalidateFrom) deletes
        // snapshots >= a trn's tradeDate; the surviving older rows can still
        // satisfy `hasSufficientHistory`, leaving the series frozen before that
        // date forever. Require the newest cached snapshot to reach endDate too.
        // SnapshotReplayer.valuationDates always includes endDate in its result,
        // and a replay emits one snapshot per valuation date, so a fresh cache
        // always has a snapshot dated endDate.
        val isFresh = !latestCached.isBefore(endDate)
        if (hasSufficientHistory && isFresh) {
            val anchored = anchorToStartDate(cached, startDate)
            log.debug(
                "Cache HIT: portfolio={}, snapshots={} (filtered from {})",
                portfolio.code,
                anchored.size,
                cached.size
            )
            return anchored
        }
        log.debug(
            "Cache PARTIAL: portfolio={}, earliest={}, latest={}, requested from {} to {}, reason={}, recomputing",
            portfolio.code,
            earliestCached,
            latestCached,
            startDate,
            endDate,
            if (!hasSufficientHistory) "insufficient history" else "stale tail"
        )
        return null
    }

    fun store(
        portfolioId: String,
        snapshots: List<CachedSnapshot>
    ) {
        try {
            if (!cacheService.isAvailable()) return
            cacheService.storeSnapshots(portfolioId, snapshots)
        } catch (e: DataAccessException) {
            log.warn("Cache store failed, computation unaffected: {}", e.message)
        }
    }

    private fun load(portfolioId: String): List<CachedSnapshot>? {
        if (!cacheService.isAvailable()) return null
        return try {
            cacheService.findAllSnapshots(portfolioId).ifEmpty { null }
        } catch (e: DataAccessException) {
            log.warn("Cache lookup failed, proceeding without cache: {}", e.message)
            null
        }
    }

    /**
     * Returns cached snapshots restricted to the requested window with a
     * baseline anchor at exactly `startDate`. When the cache lacks a snapshot
     * on that date, synthesize one by carrying forward the latest cached
     * snapshot strictly before `startDate`.
     *
     * `netContributions` and `cumulativeDividends` carry exactly because every
     * cash flow date is itself a valuation date (see `SnapshotReplayer.valuationDates`),
     * so nothing flows between two cached snapshots. `marketValue` is an
     * approximation — at most ~1 month stale, bounded by the monthly cadence
     * of valuation dates between cash-flow events.
     *
     * Without this anchor, multi-portfolio frontends that union per-portfolio
     * dates see one portfolio's series start mid-window, leaking that
     * portfolio's lifetime gain into period-relative metrics.
     */
    internal fun anchorToStartDate(
        cached: List<CachedSnapshot>,
        startDate: LocalDate
    ): List<CachedSnapshot> {
        val onOrAfter = cached.filter { !it.valuationDate.isBefore(startDate) }
        val prior = cached.lastOrNull { it.valuationDate.isBefore(startDate) }
        return when {
            onOrAfter.firstOrNull()?.valuationDate == startDate -> {
                onOrAfter
            }
            prior == null -> {
                onOrAfter
            }
            else -> {
                listOf(
                    CachedSnapshot(
                        valuationDate = startDate,
                        marketValue = prior.marketValue,
                        externalCashFlow = BigDecimal.ZERO,
                        netContributions = prior.netContributions,
                        cumulativeDividends = prior.cumulativeDividends
                    )
                ) + onOrAfter
            }
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(PerformanceSnapshotCache::class.java)
    }
}