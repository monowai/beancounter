package com.beancounter.marketdata.fx

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.cache.CacheInvalidationProducer
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.isNull
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for the scheduled FX pre-fetch job.
 */
class FxRateScheduleTest {
    private val fxProviderService = mock<FxProviderService>()
    private val fxRateRepository = mock<FxRateRepository>()
    private val cacheInvalidationProducer = mock<CacheInvalidationProducer>()
    private val dateUtils = DateUtils()

    private val usd = Currency("USD")
    private val aud = Currency("AUD")

    private fun sampleRates(date: LocalDate): List<FxRate> =
        listOf(
            FxRate(
                from = usd,
                to = aud,
                rate = BigDecimal("1.5364"),
                date = date,
                provider = "FRANKFURTER"
            )
        )

    private fun newSchedule(backfillDays: Int) =
        FxRateSchedule(
            fxProviderService = fxProviderService,
            fxRateRepository = fxRateRepository,
            dateUtils = dateUtils,
            backfillDays = backfillDays
        ).apply { setCacheInvalidationProducer(cacheInvalidationProducer) }

    @Test
    fun `fetches today plus backfill range and saves uncached dates`() {
        whenever(fxRateRepository.findByDateRange(any(), any())).thenReturn(emptyList())
        whenever(fxProviderService.getRates(any<String>(), isNull())).thenAnswer { invocation ->
            sampleRates(LocalDate.parse(invocation.arguments[0] as String))
        }

        newSchedule(backfillDays = 2).prefetchRates()

        // today + 2 backfill days = 3 calls
        verify(fxProviderService, times(3)).getRates(any<String>(), isNull())
        verify(fxRateRepository, times(3)).saveAll(any<Iterable<FxRate>>())
    }

    @Test
    fun `skips dates already cached`() {
        val today = LocalDate.now(dateUtils.zoneId)
        whenever(fxRateRepository.findByDateRange(today)).thenReturn(sampleRates(today))
        whenever(fxRateRepository.findByDateRange(today.minusDays(1))).thenReturn(emptyList())
        whenever(fxProviderService.getRates(eq(today.minusDays(1).toString()), isNull()))
            .thenReturn(sampleRates(today.minusDays(1)))

        newSchedule(backfillDays = 1).prefetchRates()

        // today is cached → no provider call for it
        verify(fxProviderService, never()).getRates(eq(today.toString()), isNull())
        // yesterday is not cached → one provider call
        verify(fxProviderService).getRates(eq(today.minusDays(1).toString()), isNull())
        verify(fxRateRepository).saveAll(any<Iterable<FxRate>>())
    }

    @Test
    fun `does not save when provider returns no rates`() {
        whenever(fxRateRepository.findByDateRange(any(), any())).thenReturn(emptyList())
        whenever(fxProviderService.getRates(any<String>(), isNull())).thenReturn(emptyList())

        newSchedule(backfillDays = 0).prefetchRates()

        verify(fxProviderService).getRates(any<String>(), isNull())
        verify(fxRateRepository, never()).saveAll(any<Iterable<FxRate>>())
    }

    @Test
    fun `prefetchRates is idempotent across consecutive runs`() {
        val today = LocalDate.now(dateUtils.zoneId)
        // First run: uncached → fetches and saves.
        whenever(fxRateRepository.findByDateRange(today)).thenReturn(emptyList())
        whenever(fxProviderService.getRates(eq(today.toString()), isNull()))
            .thenReturn(sampleRates(today))

        val schedule = newSchedule(backfillDays = 0)
        schedule.prefetchRates()

        // Second run: simulate cache populated → no further provider call.
        whenever(fxRateRepository.findByDateRange(today)).thenReturn(sampleRates(today))
        schedule.prefetchRates()

        verify(fxProviderService, times(1)).getRates(eq(today.toString()), isNull())
        verify(fxRateRepository, times(1)).saveAll(any<Iterable<FxRate>>())
        assertThat(true).isTrue() // anchor: idempotency is asserted via call counts above
    }

    @Test
    fun `emits an FX cache-invalidation event for each newly-saved date`() {
        val today = LocalDate.now(dateUtils.zoneId)
        val yesterday = today.minusDays(1)
        whenever(fxRateRepository.findByDateRange(any(), any())).thenReturn(emptyList())
        whenever(fxProviderService.getRates(any<String>(), isNull())).thenAnswer { invocation ->
            sampleRates(LocalDate.parse(invocation.arguments[0] as String))
        }

        newSchedule(backfillDays = 1).prefetchRates()

        // svc-position revalues portfolios off these events, so a freshly
        // pre-warmed FX date must publish one — otherwise persisted
        // portfolio.marketValue keeps stale FX until the next cron.
        verify(cacheInvalidationProducer).sendFxEvent(today)
        verify(cacheInvalidationProducer).sendFxEvent(yesterday)
    }

    @Test
    fun `does not emit an event for cached dates`() {
        val today = LocalDate.now(dateUtils.zoneId)
        whenever(fxRateRepository.findByDateRange(today)).thenReturn(sampleRates(today))

        newSchedule(backfillDays = 0).prefetchRates()

        verify(cacheInvalidationProducer, never()).sendFxEvent(any())
    }

    @Test
    fun `does not emit an event when the provider returns no rates`() {
        whenever(fxRateRepository.findByDateRange(any(), any())).thenReturn(emptyList())
        whenever(fxProviderService.getRates(any<String>(), isNull())).thenReturn(emptyList())

        newSchedule(backfillDays = 0).prefetchRates()

        verify(cacheInvalidationProducer, never()).sendFxEvent(any())
    }
}