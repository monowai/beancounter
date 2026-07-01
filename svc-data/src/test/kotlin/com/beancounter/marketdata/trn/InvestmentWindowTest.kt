package com.beancounter.marketdata.trn

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Verifies the rolling investment window that backs the Monthly Investment metric.
 */
class InvestmentWindowTest {
    private val today = LocalDate.of(2026, 7, 1)

    @Test
    fun `default 30-day trailing window ends today`() {
        val (start, end) = investmentWindow(DEFAULT_INVESTMENT_WINDOW_DAYS, today)
        assertThat(end).isEqualTo(today)
        assertThat(start).isEqualTo(LocalDate.of(2026, 6, 1))
    }

    @Test
    fun `custom window length is honoured`() {
        val (start, end) = investmentWindow(7, today)
        assertThat(end).isEqualTo(today)
        assertThat(start).isEqualTo(LocalDate.of(2026, 6, 24))
    }

    @Test
    fun `crosses month and year boundaries`() {
        val (start, end) = investmentWindow(30, LocalDate.of(2026, 1, 15))
        assertThat(end).isEqualTo(LocalDate.of(2026, 1, 15))
        assertThat(start).isEqualTo(LocalDate.of(2025, 12, 16))
    }

    @Test
    fun `non-positive days falls back to default`() {
        assertThat(investmentWindow(0, today).first).isEqualTo(LocalDate.of(2026, 6, 1))
        assertThat(investmentWindow(-5, today).first).isEqualTo(LocalDate.of(2026, 6, 1))
    }
}