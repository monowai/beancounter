package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import java.math.BigDecimal

/**
 * Takes quantity out of a position, recording sales and realised gain and closing the position
 * when none remains. Cash is not touched here: TrnType decides whether a type moves cash.
 */
abstract class DisposalBehaviour(
    currencyResolver: CurrencyResolver,
    private val averageCost: AverageCost
) : BaseAccumulationStrategy(currencyResolver) {
    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        var soldQuantity = trn.quantity
        if (soldQuantity.toDouble() > 0) {
            // Sign the quantities
            soldQuantity = ZERO.subtract(trn.quantity)
        }
        val quantityValues = position.quantityValues
        quantityValues.sold = quantityValues.sold.add(soldQuantity)

        // Create optimized currency context once instead of 3 separate resolutions
        val currencyContext = createCurrencyContext(trn, position)

        // Apply sell updates across all currencies efficiently
        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            updateSales(moneyValues, trn, rate, position)
        }

        return position
    }

    private fun updateSales(
        moneyValues: MoneyValues,
        trn: Trn,
        rate: BigDecimal,
        position: Position
    ) {
        val saleAmount = multiply(trn.tradeAmount, rate)
        moneyValues.sales = moneyValues.sales.add(saleAmount)

        if (trn.tradeAmount.compareTo(ZERO) != 0) {
            val unitCost =
                saleAmount?.divide(
                    trn.quantity.abs(),
                    getMathContext()
                )
            val unitProfit = unitCost?.subtract(moneyValues.averageCost)
            val realisedGain = unitProfit!!.multiply(trn.quantity.abs())
            moneyValues.realisedGain = add(moneyValues.realisedGain, realisedGain)
        }

        if (position.quantityValues.getTotal().compareTo(ZERO) == 0) {
            // Position is fully closed - reset all values and record closed date
            moneyValues.costBasis = ZERO
            moneyValues.costValue = ZERO
            moneyValues.averageCost = ZERO
            moneyValues.marketValue = ZERO
            moneyValues.unrealisedGain = ZERO
            position.quantityValues.sold = ZERO
            position.quantityValues.adjustment = ZERO
            position.quantityValues.purchased = ZERO
            position.dateValues.closed = trn.tradeDate
        }

        // If quantity changes, we need to update the cost Value
        moneyValues.costValue = averageCost.getCostValue(position, moneyValues)
    }
}