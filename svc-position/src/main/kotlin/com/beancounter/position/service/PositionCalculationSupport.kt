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
     * The position's money values in the asset's own trade currency.
     */
    fun calculateTradeMoneyValues(position: Position): MoneyValues =
        position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)

    /**
     * The position's money values in the owner's base currency.
     */
    fun calculateBaseMoneyValues(
        position: Position,
        baseCurrency: Currency
    ): MoneyValues = position.getMoneyValues(Position.In.BASE, baseCurrency)

    /**
     * The position's money values in the portfolio's reporting currency.
     */
    fun calculatePortfolioMoneyValues(
        position: Position,
        portfolioCurrency: Currency
    ): MoneyValues = position.getMoneyValues(Position.In.PORTFOLIO, portfolioCurrency)

    /**
     * A weight is dimensionless: the fraction of the portfolio a position
     * represents is the same number whichever currency you value it in. So it
     * is computed once — in the base currency, the one currency every position
     * converts to — and stamped on every bucket.
     *
     * Both sides of the ratio must share a currency. A trade-currency market
     * value over the trade-currency totals is not a ratio at all: those totals
     * sum values across every trade currency the portfolio holds, so the
     * denominator has no currency. A foreign holding weighed that way comes out
     * wrong by its own FX rate.
     */
    fun assignWeights(
        moneyValuesGroup: MoneyValuesGroup,
        baseTotals: Totals
    ) {
        val weight =
            percentUtils.percent(
                moneyValuesGroup.baseMoneyValues.marketValue,
                baseTotals.marketValue
            )
        moneyValuesGroup.tradeMoneyValues.weight = weight
        moneyValuesGroup.baseMoneyValues.weight = weight
        moneyValuesGroup.portfolioMoneyValues.weight = weight
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