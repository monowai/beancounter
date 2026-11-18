package com.beancounter.marketdata.broker.reconcile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

/** When a holding was probably opened: the first time the price came near its cost. */
class HeldSinceTest {
    private fun day(n: Int): LocalDate = LocalDate.of(2016, 1, 1).plusDays(n.toLong())

    private fun series(vararg closes: String) = closes.mapIndexed { n, close -> day(n) to BigDecimal(close) }

    @Test
    fun `should take the earliest close within ten percent of the cost, not a later one`() {
        val estimate = HeldSince.estimate(series("50", "10.5", "30", "9.8"), BigDecimal("10"))

        assertThat(estimate.heldSince).isEqualTo(day(1))
        assertThat(estimate.before).isNull()
    }

    @Test
    fun `should count a close exactly ten percent from the cost and not one further`() {
        assertThat(HeldSince.estimate(series("11.5", "11.0"), BigDecimal("10")).heldSince).isEqualTo(day(1))
        assertThat(HeldSince.estimate(series("8.5", "9.0"), BigDecimal("10")).heldSince).isEqualTo(day(1))
    }

    @Test
    fun `should take the day the price jumped across the cost when no close came near it`() {
        val estimate = HeldSince.estimate(series("5", "7", "15", "20"), BigDecimal("10"))

        assertThat(estimate.heldSince).isEqualTo(day(2))
    }

    @Test
    fun `should say only that it was before the earliest price when the cost was never reached`() {
        val estimate = HeldSince.estimate(series("50", "60", "70"), BigDecimal("10"))

        assertThat(estimate.heldSince).isNull()
        assertThat(estimate.before).isEqualTo(day(0))
    }

    @Test
    fun `should suggest nothing when the cost is above every price`() {
        val estimate = HeldSince.estimate(series("5", "6", "7"), BigDecimal("10"))

        assertThat(estimate.heldSince).isNull()
        assertThat(estimate.before).isNull()
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
        val estimate = HeldSince.estimate(series("50", "10.5", "30", "9.8").reversed(), BigDecimal("10"))

        assertThat(estimate.heldSince).isEqualTo(day(1))
    }
}