package com.beancounter.marketdata.providers

import com.beancounter.marketdata.cache.CacheInvalidationProducer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException

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
 * Submission is synchronous: prefilter (blank/cooldown/inFlight) runs on
 * the caller's thread, then the actual provider call is dispatched to
 * `priceBackfillExecutor`. Doing the prefilter inline (rather than inside
 * an `@Async` body) means cooled-down assets never reach the executor
 * queue, which is what kept the queue saturating under chart-load bursts.
 * On `RejectedExecutionException` the in-flight slot is released and the
 * submission is dropped — the cooldown will let the asset retry later.
 */
@Service
class PriceBackfillCoordinator(
    private val backfillService: MarketDataBackfillService,
    @Qualifier("priceBackfillExecutor") private val executor: TaskExecutor,
    private val cacheInvalidationProducer: CacheInvalidationProducer? = null
) {
    private val log = LoggerFactory.getLogger(PriceBackfillCoordinator::class.java)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val lastAttempt = ConcurrentHashMap<String, Instant>()

    /**
     * Queue a deep backfill for [assetId] reaching back to [fromDate]. Returns
     * `true` when the work was accepted by the executor, `false` when it was
     * filtered out (blank id, cooldown, in-flight) or the executor queue was
     * saturated. Callers can use the return value to report what was actually
     * scheduled; bursts that overflow the queue are dropped silently here so
     * the calling request isn't broken by a backpressure signal that's
     * irrelevant to it.
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
        return try {
            executor.execute { runBackfill(assetId, fromDate) }
            true
        } catch (e: RejectedExecutionException) {
            // Release the slot so the cooldown window — not a stuck in-flight
            // flag — governs the next retry attempt.
            inFlight.remove(assetId)
            log.warn("Backfill queue saturated; dropping submission for {}", assetId, e)
            false
        }
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

    private companion object {
        val ATTEMPT_COOLDOWN: Duration = Duration.ofHours(1)
    }
}