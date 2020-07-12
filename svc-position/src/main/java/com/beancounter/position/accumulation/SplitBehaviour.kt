package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class SplitBehaviour : AccumulationStrategy {
    private val averageCost = AverageCost()
    private val currencyResolver = CurrencyResolver()
    override fun accumulate(trn: Trn, portfolio: Portfolio, position: Position) {
        val total = position.quantityValues.getTotal()
        position.quantityValues
                .adjustment = trn.quantity.multiply(total).subtract(total)
        value(position, position.getMoneyValues(Position.In.TRADE,
                currencyResolver.resolve(Position.In.TRADE, portfolio, trn)))
        value(position, position.getMoneyValues(Position.In.BASE,
                currencyResolver.resolve(Position.In.BASE, portfolio, trn)))
        value(position, position.getMoneyValues(Position.In.PORTFOLIO,
                currencyResolver.resolve(Position.In.PORTFOLIO, portfolio, trn)))
    }

    private fun value(position: Position, moneyValues: MoneyValues) {
        if (moneyValues.costBasis != BigDecimal.ZERO) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, position.quantityValues.getTotal())
        }
        averageCost.setCostValue(position, moneyValues)
    }
}