package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.utils.MathUtils
import java.math.BigDecimal

/**
 * Calculate the cost of cash
 */
class CashCost(
    val averageCost: AverageCost = AverageCost()
) {
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

        // Update cost basis regardless of quantity sign
        moneyValues.costBasis = moneyValues.costBasis.add(amount).setScale(2)

        val totalQuantity = position.quantityValues.getTotal()
        if (totalQuantity.signum() != 0) {
            // Calculate average cost and cost value only if total quantity is not zero
            moneyValues.averageCost = totalQuantity.setScale(2)
            /*averageCost.value(
                moneyValues.costBasis,
                totalQuantity
            )*/
            moneyValues.costValue = totalQuantity.setScale(2)
            // averageCost.getCostValue(
            //     position,
            //     moneyValues
            // )
        } else {
            // Reset monetary values if total quantity is zero
            moneyValues.resetCosts()
        }
        // moneyValues.resetCosts() // Force cost value to zero for cash until I can figure this out.
    }
}