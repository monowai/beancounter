package com.beancounter.position.service

import com.beancounter.common.model.Currency
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Totals
import com.beancounter.common.utils.PercentUtils
import com.beancounter.position.valuation.RoiCalculator
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Data class to group totals for different currency contexts.
 */
data class TotalsGroup(
    val tradeTotals: Totals,
    val baseTotals: Totals,
    val refTotals: Totals
)

/**
 * Data class to group money values for different currency contexts.
 */
data class MoneyValuesGroup(
    val tradeMoneyValues: MoneyValues,
    val baseMoneyValues: MoneyValues,
    val portfolioMoneyValues: MoneyValues
)

/**
 * Data class to group position context information.
 */
data class PositionContext(
    val position: Position,
    val positions: Positions,
    val asAtDate: LocalDate
)

/**
 * Support class for position calculations, separating calculation logic from processing logic.
 */
@Component
class PositionCalculationSupport {
    private val percentUtils = PercentUtils()
    private val roiCalculator = RoiCalculator()

    /**
     * Calculates trade money values and sets the weight based on reference totals.
     */
    fun calculateTradeMoneyValues(
        position: Position,
        refTotals: Totals
    ): MoneyValues {
        val tradeMoneyValues = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        tradeMoneyValues.weight = percentUtils.percent(tradeMoneyValues.marketValue, refTotals.marketValue)
        return tradeMoneyValues
    }

    /**
     * Calculates base money values and sets the weight based on base totals.
     */
    fun calculateBaseMoneyValues(
        position: Position,
        baseTotals: Totals,
        baseCurrency: Currency
    ): MoneyValues {
        val baseMoneyValues = position.getMoneyValues(Position.In.BASE, baseCurrency)
        baseMoneyValues.weight = percentUtils.percent(baseMoneyValues.marketValue, baseTotals.marketValue)
        return baseMoneyValues
    }

    /**
     * Calculates portfolio money values and sets the weight based on trade totals.
     */
    fun calculatePortfolioMoneyValues(
        position: Position,
        tradeMoneyValues: MoneyValues,
        tradeTotals: Totals,
        portfolioCurrency: Currency
    ): MoneyValues {
        val portfolioMoneyValues = position.getMoneyValues(Position.In.PORTFOLIO, portfolioCurrency)
        portfolioMoneyValues.weight = percentUtils.percent(tradeMoneyValues.marketValue, tradeTotals.marketValue)
        return portfolioMoneyValues
    }

    /**
     * Calculates ROI for a given money values object.
     */
    fun calculateRoi(moneyValues: MoneyValues): BigDecimal = roiCalculator.calculateROI(moneyValues)

    /**
     * Calculates ROI for portfolio totals by creating a MoneyValues object from the totals.
     */
    fun calculatePortfolioRoi(totals: Totals): BigDecimal {
        // Create a MoneyValues object from totals for ROI calculation
        val moneyValues =
            MoneyValues(currency = totals.currency).apply {
                marketValue = totals.marketValue
                purchases = totals.purchases
                sales = totals.sales
                totalGain = totals.gain
            }
        return roiCalculator.calculateROI(moneyValues)
    }

    /**
     * Updates totals with money values, ROI, and IRR.
     */
    fun updateTotals(
        totals: Totals,
        moneyValues: MoneyValues,
        roi: BigDecimal,
        irr: BigDecimal
    ) {
        moneyValues.roi = roi
        moneyValues.irr = irr
        totals.purchases = totals.purchases.add(moneyValues.purchases)
        totals.sales = totals.sales.add(moneyValues.sales)
        totals.income = totals.income.add(moneyValues.dividends)
        totals.gain = totals.gain.add(moneyValues.totalGain)
    }

    /**
     * Updates cash totals for all currency types.
     */
    fun updateCashTotals(
        totalsGroup: TotalsGroup,
        moneyValuesGroup: MoneyValuesGroup
    ) {
        totalsGroup.tradeTotals.cash = totalsGroup.tradeTotals.cash.add(moneyValuesGroup.tradeMoneyValues.marketValue)
        totalsGroup.baseTotals.cash = totalsGroup.baseTotals.cash.add(moneyValuesGroup.baseMoneyValues.marketValue)
        totalsGroup.refTotals.cash = totalsGroup.refTotals.cash.add(moneyValuesGroup.portfolioMoneyValues.marketValue)
    }
}