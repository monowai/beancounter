package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.position.Constants.Companion.USD
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Tests for Gains calculation, especially for closed positions.
 */
class GainsTest {
    private val gains = Gains()

    @Test
    fun `should calculate totalGain for open position`() {
        // Given - position with market value and cost value
        val moneyValues = MoneyValues(USD)
        moneyValues.marketValue = BigDecimal("1500.00")
        moneyValues.costValue = BigDecimal("1000.00")
        moneyValues.realisedGain = BigDecimal("200.00")
        moneyValues.dividends = BigDecimal("50.00")

        // When
        gains.value(BigDecimal.TEN, moneyValues) // quantity = 10

        // Then
        assertThat(moneyValues.unrealisedGain).isEqualByComparingTo(BigDecimal("500.00"))
        // totalGain = unrealisedGain + dividends + realisedGain = 500 + 50 + 200 = 750
        assertThat(moneyValues.totalGain).isEqualByComparingTo(BigDecimal("750.00"))
    }

    @Test
    fun `should set unrealisedGain to zero for closed position`() {
        // Given - closed position with quantity = 0
        val moneyValues = MoneyValues(USD)
        moneyValues.marketValue = BigDecimal.ZERO // No market value for closed position
        moneyValues.costValue = BigDecimal.ZERO // Cost is zero after full sale
        moneyValues.realisedGain = BigDecimal("500.00") // Profit from the sale
        moneyValues.dividends = BigDecimal("25.00")

        // When - quantity is zero (closed position)
        gains.value(BigDecimal.ZERO, moneyValues)

        // Then - unrealisedGain should be zero
        assertThat(moneyValues.unrealisedGain).isEqualByComparingTo(BigDecimal.ZERO)
        // totalGain should equal realisedGain + dividends for closed position
        assertThat(moneyValues.totalGain).isEqualByComparingTo(BigDecimal("525.00"))
    }

    @Test
    fun `should calculate totalGain as realisedGain for closed position without dividends`() {
        // Given - closed position with only realised gains
        val moneyValues = MoneyValues(USD)
        moneyValues.realisedGain = BigDecimal("1000.00")

        // When
        gains.value(BigDecimal.ZERO, moneyValues)

        // Then - totalGain should equal realisedGain
        assertThat(moneyValues.totalGain).isEqualByComparingTo(BigDecimal("1000.00"))
    }

    @Test
    fun `should handle closed position with loss`() {
        // Given - closed position with realized loss
        val moneyValues = MoneyValues(USD)
        moneyValues.realisedGain = BigDecimal("-300.00") // Lost $300

        // When
        gains.value(BigDecimal.ZERO, moneyValues)

        // Then - totalGain should reflect the loss
        assertThat(moneyValues.unrealisedGain).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(moneyValues.totalGain).isEqualByComparingTo(BigDecimal("-300.00"))
    }
}