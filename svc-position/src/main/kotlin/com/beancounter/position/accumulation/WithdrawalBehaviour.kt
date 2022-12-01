package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a buy transaction into a position.
 */
@Service
class WithdrawalBehaviour : AccumulationStrategy {
    private val cashCost = CashCost()
    override fun accumulate(trn: Trn, positions: Positions, position: Position, portfolio: Portfolio): Position {
        val cashPosition = getCashPosition(trn, position, positions)
        val quantity = if (TrnType.isCash(trn.trnType)) trn.quantity else trn.cashAmount
        cashPosition.quantityValues.sold = position.quantityValues.sold.add(quantity)
        cashCost.value(
            trn.cashCurrency!!,
            quantity,
            portfolio,
            cashPosition,
            Position.In.TRADE,
            BigDecimal.ONE
        ) // Cash trade currency
        cashCost.value(trn.cashCurrency!!, quantity, portfolio, cashPosition, Position.In.BASE, trn.tradeBaseRate)
        cashCost.value(
            trn.cashCurrency!!,
            quantity,
            portfolio,
            cashPosition,
            Position.In.PORTFOLIO,
            trn.tradePortfolioRate
        )
        return position
    }
}
