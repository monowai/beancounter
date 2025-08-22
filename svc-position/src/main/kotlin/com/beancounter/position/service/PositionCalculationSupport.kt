package com.beancounter.position.service

import com.beancounter.common.model.Currency
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Totals
import com.beancounter.common.utils.PercentUtils
import com.beancounter.position.valuation.RoiCalculator
import org.springframework.stereotype.Component
import java.math.BigDecimal

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
    fun calculateTradeMoneyValues(position: Position, refTotals: Totals): MoneyValues {
        val tradeMoneyValues = position.getMoneyValues(Position.In.TRADE, position.asset.market.currency)
        tradeMoneyValues.weight = percentUtils.percent(tradeMoneyValues.marketValue, refTotals.marketValue)
        return tradeMoneyValues
    }

    /**
     * Calculates base money values and sets the weight based on base totals.
     */
    fun calculateBaseMoneyValues(position: Position, baseTotals: Totals, baseCurrency: Currency): MoneyValues {
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
    fun calculateRoi(moneyValues: MoneyValues): BigDecimal {
        return roiCalculator.calculateROI(moneyValues)
    }

    /**
     * Calculates ROI for portfolio totals by creating a MoneyValues object from the totals.
     */
    fun calculatePortfolioRoi(totals: Totals): BigDecimal {
        // Create a MoneyValues object from totals for ROI calculation
        val moneyValues = MoneyValues(currency = totals.currency).apply {
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
        tradeTotals: Totals,
        baseTotals: Totals,
        refTotals: Totals,
        tradeMoneyValues: MoneyValues,
        baseMoneyValues: MoneyValues,
        portfolioMoneyValues: MoneyValues
    ) {
        tradeTotals.cash = tradeTotals.cash.add(tradeMoneyValues.marketValue)
        baseTotals.cash = baseTotals.cash.add(baseMoneyValues.marketValue)
        refTotals.cash = refTotals.cash.add(portfolioMoneyValues.marketValue)
    }
}
