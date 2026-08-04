package com.beancounter.position.service

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Totals
import com.beancounter.position.Constants.Companion.SGD
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

        // When
        val result = calculationSupport.calculateTradeMoneyValues(position)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.currency).isEqualTo(USD)
    }

    @Test
    fun `should calculate base money values correctly`() {
        // Given
        val position = TestHelpers.createTestPosition()
        val baseCurrency = USD

        // When
        val result = calculationSupport.calculateBaseMoneyValues(position, baseCurrency)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.currency).isEqualTo(USD)
    }

    @Test
    fun `should calculate portfolio money values correctly`() {
        // Given
        val position = TestHelpers.createTestPosition()
        val portfolioCurrency = USD

        // When
        val result = calculationSupport.calculatePortfolioMoneyValues(position, portfolioCurrency)

        // Then
        assertThat(result).isNotNull()
        assertThat(result.currency).isEqualTo(USD)
    }

    /**
     * A foreign holding: 5 VOO valued USD 3,482 in a portfolio whose base and
     * reporting currency is SGD, where the same holding is SGD 4,462.53 of an
     * SGD 248,065.03 portfolio.
     */
    private fun foreignHolding(): MoneyValuesGroup =
        MoneyValuesGroup(
            tradeMoneyValues = MoneyValues(USD).apply { marketValue = BigDecimal("3482.00") },
            baseMoneyValues = MoneyValues(SGD).apply { marketValue = BigDecimal("4462.53") },
            portfolioMoneyValues = MoneyValues(SGD).apply { marketValue = BigDecimal("4462.53") }
        )

    @Test
    fun `weight is the same-currency ratio, not a trade value over a mixed-currency total`() {
        // Given — the trade total sums market values across every trade currency
        // the portfolio holds, so it is not a currency at all and cannot be a
        // denominator.
        val moneyValues = foreignHolding()
        val baseTotals = Totals(SGD, marketValue = BigDecimal("248065.03"))

        // When
        calculationSupport.assignWeights(moneyValues, baseTotals)

        // Then — 4,462.53 / 248,065.03 SGD, not 3,482 USD / 245,867.22 "SGD".
        assertThat(moneyValues.baseMoneyValues.weight)
            .isEqualByComparingTo(BigDecimal("0.017989"))
    }

    @Test
    fun `every bucket carries the same weight because a weight is dimensionless`() {
        // Given
        val moneyValues = foreignHolding()
        val baseTotals = Totals(SGD, marketValue = BigDecimal("248065.03"))

        // When
        calculationSupport.assignWeights(moneyValues, baseTotals)

        // Then — viewing the position in USD does not change what fraction of
        // the portfolio it is.
        assertThat(moneyValues.tradeMoneyValues.weight)
            .isEqualByComparingTo(moneyValues.baseMoneyValues.weight)
        assertThat(moneyValues.portfolioMoneyValues.weight)
            .isEqualByComparingTo(moneyValues.baseMoneyValues.weight)
    }

    @Test
    fun `an empty portfolio weighs nothing rather than dividing by zero`() {
        // Given
        val moneyValues = foreignHolding()

        // When
        calculationSupport.assignWeights(moneyValues, Totals(SGD, marketValue = BigDecimal.ZERO))

        // Then
        assertThat(moneyValues.baseMoneyValues.weight).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(moneyValues.tradeMoneyValues.weight).isEqualByComparingTo(BigDecimal.ZERO)
        assertThat(moneyValues.portfolioMoneyValues.weight).isEqualByComparingTo(BigDecimal.ZERO)
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

        // Then — total gain over capital deployed: 400 / 800 = 0.50 (sales do not reduce the basis).
        assertThat(result).isEqualByComparingTo(BigDecimal("0.50"))
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

        // Then — portfolio gain over capital deployed: 400 / 800 = 0.50 (same basis as position ROI).
        assertThat(result).isEqualByComparingTo(BigDecimal("0.50"))
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
        val totalsGroup = TotalsGroup(tradeTotals, baseTotals, refTotals)
        val moneyValuesGroup = MoneyValuesGroup(tradeMoneyValues, baseMoneyValues, portfolioMoneyValues)
        calculationSupport.updateCashTotals(totalsGroup, moneyValuesGroup)

        // Then
        assertThat(tradeTotals.cash).isEqualTo(BigDecimal("100.00"))
        assertThat(baseTotals.cash).isEqualTo(BigDecimal("200.00"))
        assertThat(refTotals.cash).isEqualTo(BigDecimal("150.00"))
    }
}