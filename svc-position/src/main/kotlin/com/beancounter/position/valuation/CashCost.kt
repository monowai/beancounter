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
        if (quantity > BigDecimal.ZERO) {
            moneyValues.purchases = moneyValues.purchases.add(MathUtils.multiply(quantity, rate))
        } else {
            moneyValues.sales = moneyValues.sales.add(MathUtils.multiply(quantity, rate))
        }
        moneyValues.costBasis = moneyValues.costBasis.add(
            MathUtils.multiply(quantity, rate),
        )
        if (position.quantityValues.getTotal().compareTo(BigDecimal.ZERO) != 0) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, position.quantityValues.getTotal())
        }
        averageCost.setCostValue(position, moneyValues)
    }
}
