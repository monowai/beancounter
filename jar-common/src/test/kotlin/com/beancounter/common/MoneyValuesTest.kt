package com.beancounter.common

import com.beancounter.common.model.Currency
import com.beancounter.common.model.MoneyValues
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Test suite for {@link MoneyValues} class to ensure that all financial operations are handled correctly.
 * This includes initializing default values and resetting cost fields.
 * Each test ensures that the {@link MoneyValues} object behaves as expected under different scenarios.
 */
class MoneyValuesTest {
    @Test
    fun `default values for MoneyObject are correct`() {
        val moneyValues = MoneyValues(TestMarkets.USD)
        Assertions.assertThat(moneyValues).hasNoNullFieldsOrPropertiesExcept(
            "priceData",
            "weight",
        )
    }

    @Test
    fun `resetCosts should set monetary values to zero`() {
        // Given a MoneyValues instance with non-zero monetary values
        val currency = Currency("USD")
        val moneyValues =
            MoneyValues(currency).apply {
                averageCost = BigDecimal("10.00")
                costValue = BigDecimal("100.00")
                costBasis = BigDecimal("1000.00")
            }

        // When resetCosts method is called
        moneyValues.resetCosts()

        // Then all specified fields should be reset to BigDecimal.ZERO
        assertEquals(
            BigDecimal.ZERO,
            moneyValues.averageCost,
            "Average cost should be reset to zero",
        )
        assertEquals(
            BigDecimal.ZERO,
            moneyValues.costValue,
            "Cost value should be reset to zero",
        )
        assertEquals(
            BigDecimal.ZERO,
            moneyValues.costBasis,
            "Cost basis should be reset to zero",
        )
    }
}
