package com.beancounter.position.valuation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.utils.MathUtils
import java.math.BigDecimal

/**
 * Calculate the cost of cash
 */
class CashCost {
    private val averageCost = AverageCost()

    fun value(
        moneyValues: MoneyValues,
        position: Position,
        quantity: BigDecimal,
        rate: BigDecimal,
    ) {
        val amount = MathUtils.multiply(quantity, rate)
        if (quantity > BigDecimal.ZERO) {
            moneyValues.purchases = moneyValues.purchases.add(amount)
        } else {
            moneyValues.sales = moneyValues.sales.add(amount)
        }
        moneyValues.costBasis =
            moneyValues.costBasis.add(
                amount,
            )
        if (position.quantityValues.getTotal().compareTo(BigDecimal.ZERO) != 0) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, position.quantityValues.getTotal())
            moneyValues.costValue = averageCost.getCostValue(position, moneyValues)
        } else {
            moneyValues.averageCost = BigDecimal.ZERO
            moneyValues.costValue = BigDecimal.ZERO
            moneyValues.costBasis = BigDecimal.ZERO // Hmm - should we hold this long term?
        }
    }
}
