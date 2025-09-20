package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.utils.MathUtils
import java.math.BigDecimal

private const val ENABLED = false

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
        rate: BigDecimal
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
        val enableCostTracking = shouldTrackCost()

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
     * @return true if cost tracking should be enabled, false otherwise
     */
    private fun shouldTrackCost(): Boolean =
        // Disable cost tracking for cash to prevent double counting and inflated cost basis
        // Transactions will still have values for display purposes
        ENABLED
}