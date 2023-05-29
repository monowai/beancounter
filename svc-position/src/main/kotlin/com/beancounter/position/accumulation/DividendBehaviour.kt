package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.multiply
import com.beancounter.position.utils.CurrencyResolver
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a dividend transaction event into a position.
 */
@Service
class DividendBehaviour : AccumulationStrategy {
    private val currencyResolver = CurrencyResolver()
    override fun accumulate(trn: Trn, positions: Positions, position: Position): Position {
        position.dateValues.lastDividend = trn.tradeDate
        value(trn, position, Position.In.TRADE, BigDecimal.ONE)
        value(trn, position, Position.In.BASE, trn.tradeBaseRate)
        value(
            trn,
            position,
            Position.In.PORTFOLIO,
            trn.tradePortfolioRate,
        )
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
        moneyValues.dividends = add(
            moneyValues.dividends,
            multiply(trn.tradeAmount, rate),
        )
    }
}
