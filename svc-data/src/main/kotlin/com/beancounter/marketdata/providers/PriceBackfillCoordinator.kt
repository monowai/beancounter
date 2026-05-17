package com.beancounter.marketdata.providers

import com.beancounter.marketdata.cache.CacheInvalidationProducer
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

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
 *
 * The `lastAttempt` map is pruned hourly so it can't grow without bound
 * across the lifetime of the process — every distinct assetId ever queried
 * would otherwise stick around forever.
 *
 * Backpressure model: prefilter (blank / cooldown / in-flight) runs on
 * the caller's thread, then a non-blocking `Semaphore.tryAcquire` reserves
 * a slot before the work is launched on the dedicated coroutine scope.
 * Exhausted permits = silent drop; the cooldown lets the asset retry on
 * the next chart load. Doing the check inline (rather than letting an
 * unbounded `launch` queue grow) is what stops chart-load bursts from
 * piling up work the system can't actually process.
 */
@Service
class PriceBackfillCoordinator(
    private val backfillService: MarketDataBackfillService,
    @Qualifier("priceBackfillScope") private val scope: CoroutineScope,
    @Value("\${price.backfill.max-permits:68}") maxPermits: Int,
    private val cacheInvalidationProducer: CacheInvalidationProducer? = null
) {
    private val log = LoggerFactory.getLogger(PriceBackfillCoordinator::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastAttempt = ConcurrentHashMap<String, Instant>()
    private val permits = Semaphore(maxPermits)

    /**
     * Queue a deep backfill for [assetId] reaching back to [fromDate]. Returns
     * `true` when the work was accepted, `false` when it was filtered out
     * (blank id, cooldown, in-flight) or no permit was available. Callers can
     * use the return value to report what was actually scheduled; saturation
     * drops are silent here so the calling request isn't broken by a
     * backpressure signal that's irrelevant to it.
     */
    fun scheduleBackfill(
        assetId: String,
        fromDate: LocalDate
    ): Boolean {
        if (assetId.isBlank()) {
            log.debug("Backfill skipped — blank assetId")
            return false
        }
        val now = Instant.now()
        val previous = lastAttempt[assetId]
        if (previous != null && Duration.between(previous, now) < ATTEMPT_COOLDOWN) {
            log.debug("Backfill cooldown active for {}, skipping", assetId)
            return false
        }
        if (!inFlight.add(assetId)) {
            log.debug("Backfill already running for {}", assetId)
            return false
        }
        lastAttempt[assetId] = now
        if (!permits.tryAcquire()) {
            // Release the slot so the cooldown window — not a stuck in-flight
            // flag — governs the next retry attempt.
            inFlight.remove(assetId)
            log.warn("Backfill capacity saturated; dropping submission for {}", assetId)
            return false
        }
        scope.launch {
            try {
                runBackfill(assetId, fromDate)
            } finally {
                permits.release()
            }
        }
        return true
    }

    private fun runBackfill(
        assetId: String,
        fromDate: LocalDate
    ) {
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

    /**
     * Drop cooldown entries older than the cooldown window — they no longer
     * block any caller, so retaining them just leaks memory in long-running
     * processes that see a steady stream of distinct assets.
     */
    @Scheduled(fixedDelayString = "PT1H")
    fun pruneStaleCooldowns() {
        val threshold = Instant.now().minus(ATTEMPT_COOLDOWN)
        val sizeBefore = lastAttempt.size
        lastAttempt.entries.removeIf { it.value.isBefore(threshold) }
        if (sizeBefore != lastAttempt.size) {
            log.debug("Pruned cooldown entries: {} -> {}", sizeBefore, lastAttempt.size)
        }
    }

    @PreDestroy
    fun shutdown() {
        scope.cancel("PriceBackfillCoordinator shutting down")
    }

    private companion object {
        val ATTEMPT_COOLDOWN: Duration = Duration.ofHours(1)
    }
}