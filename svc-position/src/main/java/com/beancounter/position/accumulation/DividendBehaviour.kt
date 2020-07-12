package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.utils.MathUtils.Companion.add
import com.beancounter.common.utils.MathUtils.Companion.divide
import com.beancounter.position.utils.CurrencyResolver
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class DividendBehaviour : AccumulationStrategy {
    private val currencyResolver = CurrencyResolver()
    override fun accumulate(trn: Trn, portfolio: Portfolio, position: Position) {
        position.dateValues.lastDividend = trn.tradeDate
        value(trn, portfolio, position, Position.In.TRADE, BigDecimal.ONE)
        value(trn, portfolio, position, Position.In.BASE, trn.tradeBaseRate)
        value(trn, portfolio, position, Position.In.PORTFOLIO,
                trn.tradePortfolioRate)
    }

    private fun value(trn: Trn,
                      portfolio: Portfolio, position: Position,
                      `in`: Position.In,
                      rate: BigDecimal?) {
        val moneyValues = position.getMoneyValues(
                `in`,
                currencyResolver.resolve(`in`, portfolio, trn)
        )
        moneyValues.dividends = add(moneyValues.dividends,
                divide(trn.tradeAmount, rate))
    }
}