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
            RoiCalculator().calculateROI(moneyValues)
                .setScale(2, RoundingMode.HALF_UP)
        assertThat(result).isEqualTo(expectedROI)
    }

    val portfolio = PortfolioUtils.getPortfolio()
    val asset = AssetUtils.getTestAsset(Constants.NASDAQ, "ABC")

    private fun getCurrentlyHeldValues(): MoneyValues {
        val positions = Positions(portfolio)
        val position = positions.getOrCreate(asset)
        val moneyValues = position.moneyValues[Position.In.TRADE]!!
        moneyValues.costValue = initialInvestment
        moneyValues.dividends = dividends
        moneyValues.marketValue = finalValue
        moneyValues.totalGain = finalValue.subtract(initialInvestment).add(dividends)
        return moneyValues
    }

    @Test
    fun `ROI should calculate with no value`() {
        val moneyValues = getSoldOut()

        val expectedROI = BigDecimal(".10") // Example expected result, assuming compounding effect
        val result =
            RoiCalculator().calculateROI(moneyValues)
                .setScale(2, RoundingMode.HALF_UP)
        assertThat(result).isEqualTo(expectedROI)
    }

    private fun getSoldOut(): MoneyValues {
        val positions = Positions(portfolio)
        val position = positions.getOrCreate(asset)
        val moneyValues = position.moneyValues[Position.In.TRADE]!!
        moneyValues.costValue = BigDecimal.ZERO
        moneyValues.dividends = dividends
        moneyValues.marketValue = BigDecimal.ZERO
        moneyValues.purchases = initialInvestment
        moneyValues.sales = finalValue
        moneyValues.totalGain = finalValue.subtract(initialInvestment).add(dividends)
        return moneyValues
    }
}
