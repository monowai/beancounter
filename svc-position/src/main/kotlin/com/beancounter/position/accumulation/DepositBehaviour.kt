package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a buy transaction into a position.
 */
@Service
class DepositBehaviour(val currencyResolver: CurrencyResolver) : AccumulationStrategy {
    private val cashCost = CashCost()
    override fun accumulate(trn: Trn, positions: Positions, position: Position): Position {
        val cashPosition = getCashPosition(trn, position, positions)
        val quantity = if (TrnType.isCash(trn.trnType)) trn.quantity else trn.cashAmount

        cashPosition.quantityValues.purchased = position.quantityValues.purchased.add(quantity)
        cashCost.value(
            currencyResolver.getMoneyValues(Position.In.TRADE, trn.cashCurrency!!, trn.portfolio, position),
            cashPosition,
            quantity,
            BigDecimal.ONE,
        ) // Cash trade currency
        cashCost.value(
            currencyResolver.getMoneyValues(Position.In.BASE, trn.cashCurrency!!, trn.portfolio, position),
            cashPosition,
            quantity,
            trn.tradeBaseRate,
        )
        cashCost.value(
            currencyResolver.getMoneyValues(Position.In.PORTFOLIO, trn.cashCurrency!!, trn.portfolio, position),
            cashPosition,
            quantity,
            trn.tradePortfolioRate,
        )
        return position
    }
}
