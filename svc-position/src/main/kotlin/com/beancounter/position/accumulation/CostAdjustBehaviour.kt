package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Adjusts the cost basis of an existing position without changing quantity or cash.
 *
 * Use cases:
 * - Return of capital distributions (reduces cost basis)
 * - Cost basis corrections
 * - Wash sale adjustments
 *
 * A positive tradeAmount increases cost basis, negative decreases it.
 */
@Service
class CostAdjustBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver(),
    private val averageCost: AverageCost = AverageCost()
) : BaseAccumulationStrategy(currencyResolver) {
    override val supportedType: TrnType
        get() = TrnType.COST_ADJUST

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        val currencyContext = createCurrencyContext(trn, position)

        applyMultiCurrencyUpdate(currencyContext, trn) { moneyValues, rate ->
            adjustCostBasis(moneyValues, trn.tradeAmount, rate, position)
        }

        return position
    }

    private fun adjustCostBasis(
        moneyValues: MoneyValues,
        tradeAmount: BigDecimal,
        rate: BigDecimal,
        position: Position
    ) {
        val adjustmentAmount = multiply(tradeAmount, rate)

        // Adjust cost basis (positive increases, negative decreases)
        moneyValues.costBasis = moneyValues.costBasis.add(adjustmentAmount)

        // Recalculate average cost if we have quantity
        val totalQuantity = position.quantityValues.getTotal()
        if (moneyValues.costBasis != ZERO && totalQuantity.compareTo(ZERO) != 0) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, totalQuantity)
        }

        // Update cost value
        moneyValues.costValue = averageCost.getCostValue(position, moneyValues)
    }
}