package com.beancounter.marketdata.broker.reconcile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/** When a holding was probably opened: the last time the price stood at its cost. */
class HeldSinceTest {
    private fun day(n: Int): LocalDate = LocalDate.of(2020, 1, 1).plusDays(n.toLong())

    private fun series(vararg closes: String) = closes.mapIndexed { n, close -> day(n) to BigDecimal(close) }

    @Test
    fun `should take the last day the price was at or below the cost when it has risen since`() {
        val estimate = HeldSince.estimate(series("8", "12", "9.5", "15", "20"), BigDecimal("10"))

        assertThat(estimate.heldSince).isEqualTo(day(2))
        assertThat(estimate.before).isNull()
    }

    @Test
    fun `should take the last day the price was at or above the cost when it has fallen since`() {
        val estimate = HeldSince.estimate(series("30", "25", "21", "18", "15"), BigDecimal("20"))

        assertThat(estimate.heldSince).isEqualTo(day(2))
    }

    @Test
    fun `should say only that it was before the earliest price when the cost was never reached`() {
        val estimate = HeldSince.estimate(series("50", "60", "70"), BigDecimal("10"))

        assertThat(estimate.heldSince).isNull()
        assertThat(estimate.before).isEqualTo(day(0))
    }

    @Test
    fun `should suggest nothing without a price history or a cost`() {
        assertThat(HeldSince.estimate(emptyList(), BigDecimal("10")).heldSince).isNull()
        assertThat(HeldSince.estimate(emptyList(), BigDecimal("10")).before).isNull()
        assertThat(HeldSince.estimate(series("5", "6"), BigDecimal.ZERO).heldSince).isNull()
        assertThat(HeldSince.estimate(series("5", "6"), BigDecimal.ZERO).before).isNull()
    }

    @Test
    fun `should use the series in date order whatever order it arrives in`() {
        val estimate = HeldSince.estimate(series("8", "12", "9.5", "15", "20").reversed(), BigDecimal("10"))

        assertThat(estimate.heldSince).isEqualTo(day(2))
    }
}