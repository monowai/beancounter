package com.beancounter.marketdata.providers

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.config.PriceScheduleProperties
import com.beancounter.marketdata.watchlist.WatchlistAlertService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Unit coverage for the market-close-aligned scheduling rework: default
 * triggers and that each configured close registers its own cron task.
 */
internal class PriceScheduleConfigurerTest {
    private val priceRefresh = Mockito.mock(PriceRefresh::class.java)
    private val watchlistAlertService = Mockito.mock(WatchlistAlertService::class.java)
    private val dateUtils = DateUtils("Asia/Singapore")

    @Test
    fun `default closes cover UK and US in their own timezones`() {
        val closes = PriceScheduleProperties().closes
        assertThat(closes).hasSize(2)

        val uk = closes.first { it.name == "UK" }
        assertThat(uk.zone).isEqualTo("Europe/London")
        assertThat(uk.cron).isEqualTo("0 30 19 * * MON-FRI")

        val us = closes.first { it.name == "US" }
        assertThat(us.zone).isEqualTo("America/New_York")
        assertThat(us.cron).isEqualTo("0 0 17 * * MON-FRI")
    }

    @Test
    fun `configureTasks registers a cron task per configured close`() {
        val schedule =
            PriceSchedule(
                priceRefresh,
                watchlistAlertService,
                dateUtils,
                PriceScheduleProperties()
            )
        val registrar = ScheduledTaskRegistrar()

        schedule.configureTasks(registrar)

        val expressions = registrar.cronTaskList.map { it.expression }
        assertThat(registrar.cronTaskList).hasSize(2)
        assertThat(expressions)
            .containsExactlyInAnyOrder(
                "0 30 19 * * MON-FRI",
                "0 0 17 * * MON-FRI"
            )
    }

    @Test
    fun `should check watchlist targets after the price refresh finishes`() {
        val schedule = PriceSchedule(priceRefresh, watchlistAlertService, dateUtils, PriceScheduleProperties())

        schedule.updatePrices()

        val order = Mockito.inOrder(priceRefresh, watchlistAlertService)
        order.verify(priceRefresh).updatePrices()
        order.verify(watchlistAlertService).checkTargets()
    }

    @Test
    fun `should still check targets when the refresh fails part way`() {
        val failing = Mockito.mock(PriceRefresh::class.java)
        Mockito.`when`(failing.updatePrices()).thenThrow(IllegalStateException("provider down"))
        val schedule = PriceSchedule(failing, watchlistAlertService, dateUtils, PriceScheduleProperties())

        // Prices saved before the failure are committed per asset, so the check still runs; the
        // refresh failure itself still surfaces to the scheduler.
        assertThatThrownBy { schedule.updatePrices() }.hasMessage("provider down")

        Mockito.verify(watchlistAlertService).checkTargets()
    }

    @Test
    fun `should not fail the scheduled run when the target check fails`() {
        val failingCheck = Mockito.mock(WatchlistAlertService::class.java)
        Mockito.`when`(failingCheck.checkTargets()).thenThrow(IllegalStateException("db down"))
        val schedule = PriceSchedule(priceRefresh, failingCheck, dateUtils, PriceScheduleProperties())

        assertThatCode { schedule.updatePrices() }.doesNotThrowAnyException()
    }
}