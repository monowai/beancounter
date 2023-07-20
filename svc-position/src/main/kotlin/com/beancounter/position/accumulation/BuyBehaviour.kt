package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a buy transaction into a position.
 */
@Service
class BuyBehaviour : AccumulationStrategy {
    private val currencyResolver = CurrencyResolver()
    private val averageCost = AverageCost()
    override fun accumulate(trn: Trn, positions: Positions, position: Position): Position {
        position.quantityValues.purchased = position.quantityValues.purchased.add(trn.quantity)
        value(trn, position, Position.In.TRADE, BigDecimal.ONE)
        value(trn, position, Position.In.BASE, trn.tradeBaseRate)
        value(trn, position, Position.In.PORTFOLIO, trn.tradePortfolioRate)
        return position
    }

    private fun value(
        trn: Trn,
        position: Position,
        `in`: Position.In,
        rate: BigDecimal,
    ) {
        val moneyValues = position.getMoneyValues(
            `in`,
            currencyResolver.resolve(`in`, trn.portfolio, trn.tradeCurrency),
        )
        moneyValues.purchases = moneyValues.purchases.add(
            multiply(trn.tradeAmount, rate),
        )
        moneyValues.costBasis = moneyValues.costBasis.add(
            multiply(trn.tradeAmount, rate),
        )
        if (moneyValues.costBasis != BigDecimal.ZERO) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, position.quantityValues.getTotal())
        }
        moneyValues.costValue = averageCost.getCostValue(position, moneyValues)
    }
}
