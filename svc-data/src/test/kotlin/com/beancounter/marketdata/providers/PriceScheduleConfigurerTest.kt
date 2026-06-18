package com.beancounter.marketdata.providers

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.config.PriceScheduleProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.scheduling.config.ScheduledTaskRegistrar

/**
 * Unit coverage for the market-close-aligned scheduling rework: default
 * triggers and that each configured close registers its own cron task.
 */
internal class PriceScheduleConfigurerTest {
    private val priceRefresh = Mockito.mock(PriceRefresh::class.java)
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
}