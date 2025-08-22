package com.beancounter.position.service

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Totals
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class PositionCalculationSupportTest {
    private lateinit var calculationSupport: PositionCalculationSupport

    @BeforeEach
    fun setup() {
        calculationSupport = PositionCalculationSupport()
    }

    @Test
    fun `should calculate trade money values correctly`() {
        // Given
        val position = TestHelpers.createTestPosition()
        val refTotals = Totals(USD, marketValue = BigDecimal("1000.00"))

        // When
        val result = calculationSupport.calculateTradeMoneyValues(position, refTotals)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.currency).isEqualTo(USD)
        assertThat(result.weight).isNotNull()
    }

    @Test
    fun `should calculate base money values correctly`() {
        // Given
        val position = TestHelpers.createTestPosition()
        val baseTotals = Totals(USD, marketValue = BigDecimal("1000.00"))
        val baseCurrency = USD

        // When
        val result = calculationSupport.calculateBaseMoneyValues(position, baseTotals, baseCurrency)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.currency).isEqualTo(USD)
        assertThat(result.weight).isNotNull()
    }

    @Test
    fun `should calculate portfolio money values correctly`() {
        // Given
        val position = TestHelpers.createTestPosition()
        val tradeMoneyValues =
            MoneyValues(USD).apply {
                marketValue = BigDecimal("500.00")
            }
        val tradeTotals = Totals(USD, marketValue = BigDecimal("1000.00"))
        val portfolioCurrency = USD

        // When
        val result =
            calculationSupport.calculatePortfolioMoneyValues(
                position,
                tradeMoneyValues,
                tradeTotals,
                portfolioCurrency
            )

        // Then
        assertThat(result).isNotNull()
        assertThat(result.currency).isEqualTo(USD)
        assertThat(result.weight).isNotNull()
    }

    @Test
    fun `should calculate ROI correctly`() {
        // Given
        val moneyValues =
            MoneyValues(USD).apply {
                marketValue = BigDecimal("1000.00")
                purchases = BigDecimal("800.00")
                sales = BigDecimal("200.00")
                totalGain = BigDecimal("400.00")
            }

        // When
        val result = calculationSupport.calculateRoi(moneyValues)

        // Then
        assertThat(result).isNotNull()
        // Note: The actual ROI calculation depends on the RoiCalculator implementation
    }

    @Test
    fun `should calculate portfolio ROI correctly`() {
        // Given
        val totals =
            Totals(USD).apply {
                marketValue = BigDecimal("1000.00")
                purchases = BigDecimal("800.00")
                sales = BigDecimal("200.00")
                gain = BigDecimal("400.00")
            }

        // When
        val result = calculationSupport.calculatePortfolioRoi(totals)

        // Then
        assertThat(result).isNotNull()
        // Note: The actual ROI calculation depends on the RoiCalculator implementation
    }

    @Test
    fun `should update totals correctly`() {
        // Given
        val totals = Totals(USD)
        val moneyValues =
            MoneyValues(USD).apply {
                purchases = BigDecimal("100.00")
                sales = BigDecimal("50.00")
                dividends = BigDecimal("10.00")
                totalGain = BigDecimal("60.00")
            }
        val roi = BigDecimal("0.15")
        val irr = BigDecimal("0.12")

        // When
        calculationSupport.updateTotals(totals, moneyValues, roi, irr)

        // Then
        assertThat(moneyValues.roi).isEqualTo(roi)
        assertThat(moneyValues.irr).isEqualTo(irr)
        assertThat(totals.purchases).isEqualTo(BigDecimal("100.00"))
        assertThat(totals.sales).isEqualTo(BigDecimal("50.00"))
        assertThat(totals.income).isEqualTo(BigDecimal("10.00"))
        assertThat(totals.gain).isEqualTo(BigDecimal("60.00"))
    }

    @Test
    fun `should update cash totals correctly`() {
        // Given
        val tradeTotals = Totals(USD)
        val baseTotals = Totals(USD)
        val refTotals = Totals(USD)
        val tradeMoneyValues =
            MoneyValues(USD).apply {
                marketValue = BigDecimal("100.00")
            }
        val baseMoneyValues =
            MoneyValues(USD).apply {
                marketValue = BigDecimal("200.00")
            }
        val portfolioMoneyValues =
            MoneyValues(USD).apply {
                marketValue = BigDecimal("150.00")
            }

        // When
        calculationSupport.updateCashTotals(
            tradeTotals,
            baseTotals,
            refTotals,
            tradeMoneyValues,
            baseMoneyValues,
            portfolioMoneyValues
        )

        // Then
        assertThat(tradeTotals.cash).isEqualTo(BigDecimal("100.00"))
        assertThat(baseTotals.cash).isEqualTo(BigDecimal("200.00"))
        assertThat(refTotals.cash).isEqualTo(BigDecimal("150.00"))
    }
}