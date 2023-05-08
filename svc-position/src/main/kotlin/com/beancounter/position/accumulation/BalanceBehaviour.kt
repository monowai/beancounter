package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to accumulate a buy transaction into a position.
 */
@Service
class BalanceBehaviour : AccumulationStrategy {
    private val cashCost = CashCost()
    override fun accumulate(trn: Trn, positions: Positions, position: Position, portfolio: Portfolio): Position {
        position.quantityValues.purchased = trn.tradeAmount
        cashCost.value(
            trn.tradeCurrency,
            trn.tradeAmount,
            portfolio,
            position,
            Position.In.BASE,
            trn.tradeBaseRate,
        )
        cashCost.value(
            trn.tradeCurrency,
            trn.tradeAmount,
            portfolio,
            position,
            Position.In.PORTFOLIO,
            trn.tradePortfolioRate,
        )
        cashCost.value(
            trn.tradeCurrency,
            trn.tradeAmount,
            portfolio,
            position,
            Position.In.TRADE,
            BigDecimal.ONE,
        ) // Trade to Cash Settlement ?
        return position
    }
}
