package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.PortfolioUtils
import com.beancounter.position.Constants
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Validate ROI Calcs.
 */
class RoiCalculationTests {
    private val initialInvestment = BigDecimal("1000")
    private val dividends = BigDecimal("50")
    private val finalValue = BigDecimal("1050")

    @Test
    fun `ROI should calculate with market value`() {
        val moneyValues = getCurrentlyHeldValues()

        val expectedROI = BigDecimal(".10") // Example expected result, assuming compounding effect
        val result =
            RoiCalculator()
                .calculateROI(moneyValues)
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                )
        assertThat(result).isEqualTo(expectedROI)
    }

    val portfolio = PortfolioUtils.getPortfolio()
    val asset =
        AssetUtils.getTestAsset(
            Constants.NASDAQ,
            "ABC"
        )

    private fun getCurrentlyHeldValues(): MoneyValues {
        val positions = Positions(portfolio)
        val position = positions.getOrCreate(asset)
        val moneyValues = position.moneyValues[Position.In.BASE]!!
        // Never-sold position: purchases equals the residual cost of the holding.
        moneyValues.purchases = initialInvestment
        moneyValues.costValue = initialInvestment
        moneyValues.dividends = dividends
        moneyValues.marketValue = finalValue
        moneyValues.totalGain = finalValue.subtract(initialInvestment).add(dividends)
        return moneyValues
    }

    @Test
    fun `ROI on a partially-sold position uses total capital deployed, not residual cost`() {
        val positions = Positions(portfolio)
        val position = positions.getOrCreate(asset)
        val moneyValues = position.moneyValues[Position.In.BASE]!!
        // Bought 6000 of capital over the life of the position (never decremented).
        moneyValues.purchases = BigDecimal("6000")
        // Sold most of it, banking a realised gain.
        moneyValues.sales = BigDecimal("5000")
        moneyValues.realisedGain = BigDecimal("1000")
        // The residual holding costs 1200 and is now worth 1500.
        moneyValues.costValue = BigDecimal("1200")
        moneyValues.marketValue = BigDecimal("1500")
        moneyValues.unrealisedGain = BigDecimal("300")
        moneyValues.dividends = BigDecimal("100")
        moneyValues.totalGain = BigDecimal("1400") // 300 unrealised + 100 dividends + 1000 realised

        val result =
            RoiCalculator()
                .calculateROI(moneyValues)
                .setScale(4, RoundingMode.HALF_UP)

        // 1400 / 6000 = 0.2333 (total return on capital deployed).
        // The residual-cost basis would wrongly yield 1400 / 1200 = 1.1667.
        assertThat(result).isEqualTo(BigDecimal("0.2333"))
    }

    @Test
    fun `ROI should calculate with no value`() {
        val moneyValues = getSoldOut()

        val expectedROI = BigDecimal(".10") // Example expected result, assuming compounding effect
        val result =
            RoiCalculator()
                .calculateROI(moneyValues)
                .setScale(
                    2,
                    RoundingMode.HALF_UP
                )
        assertThat(result).isEqualTo(expectedROI)
    }

    private fun getSoldOut(): MoneyValues {
        val positions = Positions(portfolio)
        val position = positions.getOrCreate(asset)
        val moneyValues = position.moneyValues[Position.In.BASE]!!
        moneyValues.costValue = BigDecimal.ZERO
        moneyValues.dividends = dividends
        moneyValues.marketValue = BigDecimal.ZERO
        moneyValues.purchases = initialInvestment
        moneyValues.sales = finalValue
        moneyValues.totalGain = finalValue.subtract(initialInvestment).add(dividends)
        return moneyValues
    }
}