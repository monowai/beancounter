package com.beancounter.common.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Cost leaves a position with the quantity that carried it.
 */
class MoneyValuesTest {
    private fun costing(cost: String) = MoneyValues(Currency("USD")).apply { costBasis = BigDecimal(cost) }

    @Test
    fun `should relieve the disposed share of the cost`() {
        val moneyValues = costing("1000")
        moneyValues.relieveCost(BigDecimal("40"), BigDecimal("100"))

        assertThat(moneyValues.costBasis).isEqualByComparingTo("600")
    }

    @Test
    fun `should round the relieved share to cents`() {
        val moneyValues = costing("2100.23")
        moneyValues.relieveCost(BigDecimal("3"), BigDecimal("10"))

        assertThat(moneyValues.costBasis).isEqualByComparingTo("1470.16")
    }

    @Test
    fun `should leave no cost once everything held is disposed of`() {
        val moneyValues = costing("1470.16")
        moneyValues.relieveCost(BigDecimal("7"), BigDecimal("7"))

        assertThat(moneyValues.costBasis).isEqualByComparingTo("0")
    }
}