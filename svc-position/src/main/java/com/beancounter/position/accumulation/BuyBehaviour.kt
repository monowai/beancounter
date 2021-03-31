package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
/**
 * Logic to accumulate a buy transaction into a position.
 */
class BuyBehaviour : AccumulationStrategy {
    private val currencyResolver = CurrencyResolver()
    private val averageCost = AverageCost()
    override fun accumulate(trn: Trn, portfolio: Portfolio, position: Position) {
        val quantityValues = position.quantityValues
        quantityValues.purchased = quantityValues.purchased.add(trn.quantity)
        value(trn, portfolio, position, Position.In.TRADE, BigDecimal.ONE)
        value(trn, portfolio, position, Position.In.BASE, trn.tradeBaseRate)
        value(trn, portfolio, position, Position.In.PORTFOLIO, trn.tradePortfolioRate)
    }

    private fun value(
        trn: Trn,
        portfolio: Portfolio,
        position: Position,
        `in`: Position.In,
        rate: BigDecimal?
    ) {
        val moneyValues = position.getMoneyValues(
            `in`,
            currencyResolver.resolve(`in`, portfolio, trn)
        )
        moneyValues.purchases = moneyValues.purchases.add(
            divide(trn.tradeAmount, rate)
        )
        moneyValues.costBasis = moneyValues.costBasis.add(
            divide(trn.tradeAmount, rate)
        )
        if (moneyValues.costBasis != BigDecimal.ZERO) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, position.quantityValues.getTotal())
        }
        averageCost.setCostValue(position, moneyValues)
    }
}
