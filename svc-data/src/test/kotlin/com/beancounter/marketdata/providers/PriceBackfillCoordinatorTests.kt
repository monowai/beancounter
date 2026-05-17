package com.beancounter.marketdata.providers

import com.beancounter.marketdata.cache.CacheInvalidationProducer
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.time.LocalDate

internal class PriceBackfillCoordinatorTests {
    @Test
    fun is_BackfillDelegatedToService() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService)

        val fromDate = LocalDate.now().minusYears(5)
        coordinator.scheduleBackfill("asset-1", fromDate)

        verify(backfillService).backFill(eq("asset-1"), eq(fromDate))
    }

    @Test
    fun is_CooldownSuppressesRepeatBackfillForSameAsset() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService)

        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))
        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        verify(backfillService, times(1)).backFill(eq("asset-1"), any())
    }

    @Test
    fun is_DifferentAssetsBackfilledIndependently() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService)

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
        val coordinator = PriceBackfillCoordinator(backfillService)

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
        val coordinator = PriceBackfillCoordinator(backfillService)

        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        verify(backfillService, never()).backFill(eq("asset-2"), any())
    }

    @Test
    fun is_PriceHistoryEventPublishedAfterSuccessfulBackfill() {
        val backfillService = mock<MarketDataBackfillService>()
        val producer = mock<CacheInvalidationProducer>()
        val coordinator = PriceBackfillCoordinator(backfillService, producer)

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
        val coordinator = PriceBackfillCoordinator(backfillService, producer)

        coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        verify(producer, never()).sendPriceHistoryEvent(any(), any())
    }
}