package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.MathUtils
import java.math.BigDecimal

/**
 * Calculate the cost of cash with selective calculation based on transaction type.
 *
 * Cost tracking approach:
 * - FX transactions (FX_BUY): Track actual exchange cost
 * - Trade settlements (BUY/SELL): Track settlement amount as cost
 * - Simple cash (DEPOSIT/WITHDRAWAL): No cost tracking (1:1 rate)
 */
class CashCost(
    val averageCost: AverageCost = AverageCost()
) {
    /**
     * Calculate cash cost with selective tracking based on transaction type.
     */
    fun value(
        moneyValues: MoneyValues,
        position: Position,
        quantity: BigDecimal,
        rate: BigDecimal,
        trnType: TrnType? = null
    ) {
        val amount =
            MathUtils.multiply(
                quantity,
                rate
            ) ?: return

        // Update purchases or sales based on the sign of the quantity
        if (quantity > BigDecimal.ZERO) {
            moneyValues.purchases = moneyValues.purchases.add(amount)
        } else if (quantity < BigDecimal.ZERO) {
            moneyValues.sales = moneyValues.sales.add(amount)
        }

        // Determine if cost tracking should be enabled for this transaction type
        val enableCostTracking = shouldTrackCost(trnType)

        if (enableCostTracking) {
            // Update cost basis for transactions where cost tracking makes sense
            moneyValues.costBasis = moneyValues.costBasis.add(amount.abs())

            val totalQuantity = position.quantityValues.getTotal()
            if (totalQuantity.signum() != 0) {
                // Calculate average cost and cost value only if total quantity is not zero
                moneyValues.averageCost =
                    averageCost.value(
                        moneyValues.costBasis,
                        totalQuantity.abs()
                    )
                moneyValues.costValue =
                    averageCost.getCostValue(
                        position,
                        moneyValues
                    )
            } else {
                // Reset monetary values if total quantity is zero
                moneyValues.resetCosts()
            }
        } else {
            // For simple cash transactions, reset cost values to zero
            moneyValues.resetCosts()
        }
    }

    /**
     * Determine if cost tracking should be enabled for the given transaction type.
     *
     * Cost tracking approach for cash positions:
     * - FX transactions: Track actual exchange costs
     * - Trade settlements: Track settlement amounts to maintain cost basis of cash flows
     * - Simple cash movements: No cost tracking (1:1 rate)
     *
     * @param trnType The transaction type to check
     * @return true if cost tracking should be enabled, false otherwise
     */
    private fun shouldTrackCost(trnType: TrnType?): Boolean =
        when (trnType) {
            TrnType.FX_BUY -> true // FX transactions have real exchange costs
            TrnType.BUY -> true // Track cash paid for equity purchases
            TrnType.SELL -> true // Track cash received from equity sales
            TrnType.DIVI -> true // Track dividend cash received
            TrnType.DEPOSIT -> false // Simple deposits are 1:1, no cost tracking needed
            TrnType.WITHDRAWAL -> false // Simple withdrawals are 1:1, no cost tracking needed
            TrnType.BALANCE -> false // Balance adjustments don't affect cost
            TrnType.SPLIT -> false // Splits don't change total cost basis
            TrnType.ADD -> false // ADD transactions don't impact cash
            TrnType.IGNORE -> false // Ignored transactions
            null -> false // Default to no cost tracking when type is unknown
        }
}