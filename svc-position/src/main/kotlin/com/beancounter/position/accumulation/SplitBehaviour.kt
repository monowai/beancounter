package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.AverageCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a split transaction event into a position.
 */
@Service
class SplitBehaviour(
    val currencyResolver: CurrencyResolver = CurrencyResolver(),
    val averageCost: AverageCost = AverageCost(),
) : AccumulationStrategy {
    override val supportedType: TrnType
        get() = TrnType.SPLIT

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position,
    ): Position {
        val total = position.quantityValues.getTotal()
        position.quantityValues
            .adjustment = trn.quantity.multiply(total).subtract(total)
        value(
            position,
            position.getMoneyValues(
                Position.In.TRADE,
                currencyResolver.resolve(Position.In.TRADE, trn.portfolio, trn.tradeCurrency),
            ),
        )
        value(
            position,
            position.getMoneyValues(
                Position.In.BASE,
                currencyResolver.resolve(Position.In.BASE, trn.portfolio, trn.tradeCurrency),
            ),
        )
        value(
            position,
            position.getMoneyValues(
                Position.In.PORTFOLIO,
                currencyResolver.resolve(Position.In.PORTFOLIO, trn.portfolio, trn.tradeCurrency),
            ),
        )
        return position
    }

    private fun value(
        position: Position,
        moneyValues: MoneyValues,
    ) {
        if (moneyValues.costBasis != BigDecimal.ZERO) {
            moneyValues.averageCost = averageCost.value(moneyValues.costBasis, position.quantityValues.getTotal())
        }
        moneyValues.costValue = averageCost.getCostValue(position, moneyValues)
    }
}
