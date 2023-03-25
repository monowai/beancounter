package com.beancounter.position.valuation

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.utils.MathUtils
import com.beancounter.position.utils.CurrencyResolver
import java.math.BigDecimal

/**
 * Calculate the cost of cash
 */
class CashCost {
    private val currencyResolver = CurrencyResolver()
    private val averageCost = AverageCost()

    fun value(
        currency: Currency,
        quantity: BigDecimal,
        portfolio: Portfolio,
        position: Position,
        `in`: Position.In,
        rate: BigDecimal?,
    ) {
        val moneyValues = position.getMoneyValues(
            `in`,
            currencyResolver.resolve(`in`, portfolio, currency),
        )
        if (quantity > BigDecimal.ZERO) {
            moneyValues.purchases = moneyValues.purchases.add(MathUtils.divide(quantity, rate))
        } else {
            moneyValues.sales = moneyValues.sales.add(MathUtils.divide(quantity, rate))
        }
        moneyValues.costBasis = moneyValues.costBasis.add(
            MathUtils.divide(quantity, rate),
        )
        if (position.quantityValues.getTotal().compareTo(BigDecimal.ZERO) != 0) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, position.quantityValues.getTotal())
        }
        averageCost.setCostValue(position, moneyValues)
    }
}
