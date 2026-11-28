package com.beancounter.position.accumulation

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.common.utils.MathUtils.Companion.getMathContext
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.marketvalue.AverageCost
import java.math.BigDecimal

/**
 * Takes quantity out of a position, recording sales and realised gain and closing the position
 * when none remains. The disposal takes its share of the cost with it, so the cost basis stays
 * the cost of what is still held. A disposal of more than is held is rejected: a short position
 * needs its own accounting. Cash is not touched here: TrnType decides whether a type moves cash.
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
        // A disposal may arrive signed either way; sold is held negative.
        val disposed = trn.quantity.abs()
        val quantityValues = position.quantityValues
        val held = quantityValues.getTotal()
        if (disposed > held) {
            throw BusinessException(
                "${trn.trnType} of $disposed ${trn.asset.code} on ${trn.tradeDate} " +
                    "is more than the $held held; short positions are not supported"
            )
        }
        quantityValues.sold = quantityValues.sold.subtract(disposed)

        // Create optimized currency context once instead of 3 separate resolutions
        val currencyContext = createCurrencyContext(trn, position)

        // Apply sell updates across all currencies efficiently
        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            updateSales(moneyValues, trn, rate, position)
            relieveCost(moneyValues, disposed, held)
        }

        return position
    }

    private fun updateSales(
        moneyValues: MoneyValues,
        trn: Trn,
        rate: BigDecimal,
        position: Position
    ) {
        // multiply returns null only for a null amount; tradeAmount is never null.
        val saleAmount = checkNotNull(multiply(trn.tradeAmount, rate))
        moneyValues.sales = moneyValues.sales.add(saleAmount)

        if (trn.tradeAmount.compareTo(ZERO) != 0) {
            val unitCost =
                saleAmount.divide(
                    trn.quantity.abs(),
                    getMathContext()
                )
            val unitProfit = unitCost.subtract(moneyValues.averageCost)
            val realisedGain = unitProfit.multiply(trn.quantity.abs())
            moneyValues.realisedGain = add(moneyValues.realisedGain, realisedGain)
        }

        if (!position.quantityValues.hasPosition()) {
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

    private fun relieveCost(
        moneyValues: MoneyValues,
        disposed: BigDecimal,
        held: BigDecimal
    ) {
        moneyValues.costBasis =
            moneyValues.costBasis.subtract(divide(moneyValues.costBasis.multiply(disposed), held))
    }
}