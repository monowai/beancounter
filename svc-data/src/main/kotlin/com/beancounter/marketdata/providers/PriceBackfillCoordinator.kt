package com.beancounter.marketdata.providers

import com.beancounter.marketdata.cache.CacheInvalidationProducer
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Schedules deep price-history backfills outside the request thread.
 *
 * `PriceController.getPriceHistory` returns whatever is in the DB straight
 * away. When the cached range doesn't cover what the chart asked for, we
 * trigger an async backfill here so the next request gets the extended
 * history without blocking the current one on a multi-year provider call.
 *
 * In-flight guard prevents two concurrent backfills for the same asset.
 * Cooldown prevents retry storms when the provider call keeps failing.
 */
@Service
class PriceBackfillCoordinator(
    private val backfillService: MarketDataBackfillService,
    private val cacheInvalidationProducer: CacheInvalidationProducer? = null
) {
    private val log = LoggerFactory.getLogger(PriceBackfillCoordinator::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastAttempt = ConcurrentHashMap<String, Instant>()

    /**
     * Schedule a deep historical price backfill for the given asset starting from the specified date.
     *
     * This method will skip scheduling when `assetId` is blank, enforce a per-asset retry cooldown (1 hour),
     * and avoid starting a second concurrent backfill for the same asset. When allowed, it records the attempt
     * time, invokes the backfill service, optionally emits a cache-invalidation event if a producer is configured,
     * logs progress, and ensures the in-flight marker is cleared even on error. Exceptions from the backfill are caught
     * and logged rather than propagated.
     *
     * @param assetId The identifier of the asset to backfill.
     * @param fromDate The inclusive start date from which price history should be backfilled.
     */
    @Async("priceBackfillExecutor")
    fun scheduleBackfill(
        assetId: String,
        fromDate: LocalDate
    ) {
        if (assetId.isBlank()) {
            log.debug("Backfill skipped — blank assetId")
            return
        }
        val now = Instant.now()
        val previous = lastAttempt[assetId]
        if (previous != null && Duration.between(previous, now) < ATTEMPT_COOLDOWN) {
            log.debug("Backfill cooldown active for {}, skipping", assetId)
            return
        }
        if (!inFlight.add(assetId)) {
            log.debug("Backfill already running for {}", assetId)
            return
        }
        lastAttempt[assetId] = now
        try {
            log.info("Async backfill starting for asset={} fromDate={}", assetId, fromDate)
            backfillService.backFill(assetId, fromDate)
            // Notify downstream caches (e.g. svc-position performance snapshots)
            // that this asset's history changed so they recompute on the next
            // request. Producer is optional in tests / cache-disabled profiles.
            cacheInvalidationProducer?.sendPriceHistoryEvent(assetId, fromDate)
            log.info("Async backfill completed for asset={}", assetId)
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            log.warn("Async backfill failed for asset {}", assetId, e)
        } finally {
            inFlight.remove(assetId)
        }
    }

    private companion object {
        val ATTEMPT_COOLDOWN: Duration = Duration.ofHours(1)
    }
}