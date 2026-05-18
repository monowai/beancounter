package com.beancounter.marketdata.providers

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
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
    // Unconfined keeps `launch` synchronous from the caller's POV: the body
    // runs on the current thread until the first suspension, and `runBackfill`
    // never suspends, so by the time `scheduleBackfill` returns the work and
    // its `finally` blocks have already executed. SupervisorJob isolates a
    // failed test from sibling launches that may share the scope.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @AfterEach
    fun tearDown() {
        scope.cancel("test cleanup")
    }

    @Test
    fun is_BackfillDelegatedToService() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService, scope, DEFAULT_PERMITS)

        val fromDate = LocalDate.now().minusYears(5)
        val accepted = coordinator.scheduleBackfill("asset-1", fromDate)

        assertThat(accepted).isTrue()
        verify(backfillService).backFill(eq("asset-1"), eq(fromDate))
    }

    @Test
    fun is_CooldownSuppressesRepeatBackfillForSameAsset() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService, scope, DEFAULT_PERMITS)

        val first = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))
        val second = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        assertThat(first).isTrue()
        assertThat(second).isFalse()
        verify(backfillService, times(1)).backFill(eq("asset-1"), any())
    }

    @Test
    fun is_DifferentAssetsBackfilledIndependently() {
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator = PriceBackfillCoordinator(backfillService, scope, DEFAULT_PERMITS)

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
        val coordinator = PriceBackfillCoordinator(backfillService, scope, DEFAULT_PERMITS)

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
        val coordinator = PriceBackfillCoordinator(backfillService, scope, DEFAULT_PERMITS)

        val blank = coordinator.scheduleBackfill("", LocalDate.now().minusYears(5))
        val whitespace = coordinator.scheduleBackfill("   ", LocalDate.now().minusYears(5))

        assertThat(blank).isFalse()
        assertThat(whitespace).isFalse()
        verify(backfillService, never()).backFill(any<String>(), any<LocalDate>())
    }

    @Test
    fun is_SaturatedCapacityDropsSubmission() {
        // Zero permits = every submission saturates. Simulates the load
        // condition that surfaced in DATA-4P. The coordinator must drop the
        // request, return false, release the in-flight slot, and never call
        // the service.
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator =
            PriceBackfillCoordinator(backfillService, scope, maxPermits = 0)

        val accepted = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))

        assertThat(accepted).isFalse()
        verify(backfillService, never()).backFill(any<String>(), any<LocalDate>())
    }

    @Test
    fun is_PermitReleasedAfterCompletion() {
        // With one permit and an Unconfined dispatcher, the launch body
        // (including the `finally` that releases the permit) runs before
        // `scheduleBackfill` returns. A second submission for a *different*
        // asset must therefore find the permit available again — proving the
        // semaphore isn't leaking permits on the happy path.
        val backfillService = mock<MarketDataBackfillService>()
        val coordinator =
            PriceBackfillCoordinator(backfillService, scope, maxPermits = 1)

        val first = coordinator.scheduleBackfill("asset-1", LocalDate.now().minusYears(5))
        val second = coordinator.scheduleBackfill("asset-2", LocalDate.now().minusYears(5))

        assertThat(first).isTrue()
        assertThat(second).isTrue()
        verify(backfillService, times(2)).backFill(any<String>(), any<LocalDate>())
    }

    private companion object {
        const val DEFAULT_PERMITS = 4
    }
}