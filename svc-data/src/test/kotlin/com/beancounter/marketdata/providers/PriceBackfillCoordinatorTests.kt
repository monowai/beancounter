package com.beancounter.marketdata.providers

import com.beancounter.marketdata.cache.CacheInvalidationProducer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.core.task.SyncTaskExecutor
import org.springframework.core.task.TaskExecutor
import java.time.LocalDate
import java.util.concurrent.RejectedExecutionException

internal class PriceBackfillCoordinatorTests {
    private val syncExecutor: TaskExecutor = SyncTaskExecutor()

    @Test
    fun is_BackfillDelegatedToService() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService, syncExecutor)

        val fromDate = LocalDate.now().minusYears(5)
        val accepted = coordinator.scheduleBackfill("asset-1", fromDate)

        assertThat(accepted).isTrue()
        verify(backfillService).backFill(eq("asset-1"), eq(fromDate))
    }

    @Test
    fun is_CooldownSuppressesRepeatBackfillForSameAsset() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService, syncExecutor)

        val first = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))
        val second = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        assertThat(first).isTrue()
        assertThat(second).isFalse()
        verify(backfillService, times(1)).backFill(eq("asset-1"), any())
    }

    @Test
    fun is_DifferentAssetsBackfilledIndependently() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService, syncExecutor)

        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))
        coordinator.scheduleBackfill("asset-2", LocalDate.now().minusYears(5))

        verify(backfillService).backFill(eq("asset-1"), any())
        verify(backfillService).backFill(eq("asset-2"), any())
    }

    @Test
    fun is_BackfillFailureDoesNotLeakInFlightFlag() {
        val backfillService = mock<MarketDataBackfillService>()
        whenever(backfillService.backFill(eq("asset-1"), any()))
            .thenThrow(RuntimeException("provider failure"))
        val coordinator = PriceBackfillCoordinator(backfillService, syncExecutor)

        // First call fails inside the catch — the in-flight set should be
        // cleared in the finally block so a later (post-cooldown) call could
        // try again. We can't tick the cooldown in a unit test, but we can at
        // least verify the failure didn't propagate and the second call is
        // silently suppressed by cooldown rather than crashing.
        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))
        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        verify(backfillService, times(1)).backFill(eq("asset-1"), any())
    }

    @Test
    fun is_BackfillNotInvokedForBlankAsset() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService, syncExecutor)

        val blank = coordinator.scheduleBackfill("", LocalDate.now().minusYears(5))
        val whitespace = coordinator.scheduleBackfill("   ", LocalDate.now().minusYears(5))

        assertThat(blank).isFalse()
        assertThat(whitespace).isFalse()
        verify(backfillService, never()).backFill(any<String>(), any<LocalDate>())
    }

    @Test
    fun is_PriceHistoryEventPublishedAfterSuccessfulBackfill() {
        val backfillService = mock<MarketDataBackfillService>()
        val producer = mock<CacheInvalidationProducer>()
        val coordinator = PriceBackfillCoordinator(backfillService, syncExecutor, producer)

        val fromDate = LocalDate.now().minusYears(5)
        coordinator.scheduleBackfill("asset-1", fromDate)

        verify(producer).sendPriceHistoryEvent(eq("asset-1"), eq(fromDate))
    }

    @Test
    fun is_NoEventPublishedWhenBackfillFails() {
        val backfillService = mock<MarketDataBackfillService>()
        whenever(backfillService.backFill(eq("asset-1"), any()))
            .thenThrow(RuntimeException("provider failure"))
        val producer = mock<CacheInvalidationProducer>()
        val coordinator = PriceBackfillCoordinator(backfillService, syncExecutor, producer)

        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        verify(producer, never()).sendPriceHistoryEvent(any(), any())
    }

    @Test
    fun is_RejectedExecutionDroppedNotThrown() {
        // Simulates the saturated-queue case that surfaced in DATA-4P. The
        // executor rejects the submission; the coordinator must swallow the
        // throw, return false, and release the in-flight slot so the next
        // (post-cooldown) attempt isn't permanently blocked.
        val backfillService = mock<MarketDataBackfillService>()
        val rejectingExecutor =
            TaskExecutor { _ ->
                throw RejectedExecutionException("queue saturated")
            }
        val coordinator = PriceBackfillCoordinator(backfillService, rejectingExecutor)

        val accepted = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        assertThat(accepted).isFalse()
        verify(backfillService, never()).backFill(any<String>(), any<LocalDate>())
    }

    @Test
    fun is_RejectedExecutionReleasesInFlightSlot() {
        // After a rejection, a follow-up submission on a working executor
        // should still get through (i.e. the slot wasn't left marked). The
        // cooldown will still block re-submission for the same asset, so we
        // verify slot-release on a different asset that succeeds.
        val backfillService = mock<MarketDataBackfillService>()
        var rejectNext = true
        val flakyExecutor =
            TaskExecutor { task ->
                if (rejectNext) {
                    rejectNext = false
                    throw RejectedExecutionException("queue saturated")
                }
                task.run()
            }
        val coordinator = PriceBackfillCoordinator(backfillService, flakyExecutor)

        val first = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))
        val second = coordinator.scheduleBackfill("asset-2", LocalDate.now().minusYears(5))

        assertThat(first).isFalse()
        assertThat(second).isTrue()
        verify(backfillService, never()).backFill(eq("asset-1"), any())
        verify(backfillService).backFill(eq("asset-2"), any())
    }
}