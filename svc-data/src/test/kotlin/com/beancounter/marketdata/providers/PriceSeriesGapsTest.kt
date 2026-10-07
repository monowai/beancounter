package com.beancounter.marketdata.providers

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class PriceSeriesGapsTest {
    private val monday = LocalDate.of(2026, 9, 7)

    @Test
    fun `should tolerate weekends and a four day holiday weekend`() {
        val dates =
            listOf(
                monday.minusDays(3), // Fri
                monday, // Mon
                monday.plusDays(1),
                monday.plusDays(4), // Fri
                monday.plusDays(8) // Tue after a long weekend
            )

        assertThat(PriceSeriesGaps.firstGapStart(dates)).isNull()
    }

    @Test
    fun `should return the last stored date before the first hole wider than the tolerance`() {
        val dates =
            listOf(
                monday,
                monday.plusDays(1),
                monday.plusDays(1 + PriceSeriesGaps.MAX_CALENDAR_GAP_DAYS + 1),
                monday.plusDays(60)
            )

        assertThat(PriceSeriesGaps.firstGapStart(dates)).isEqualTo(monday.plusDays(1))
    }

    @Test
    fun `should report no gap for an empty or single point series`() {
        assertThat(PriceSeriesGaps.firstGapStart(emptyList())).isNull()
        assertThat(PriceSeriesGaps.firstGapStart(listOf(monday))).isNull()
    }
}