package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Logic to Add a balance transaction into a position.
 * These are psuedo cash transactions that do not support cashAssetId.
 */
@Service
class BalanceBehaviour(val currencyResolver: CurrencyResolver) : AccumulationStrategy {
    private val cashCost = CashCost()

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position,
    ): Position {
        position.quantityValues.purchased = trn.tradeAmount
        cashCost.value(
            currencyResolver.getMoneyValues(Position.In.BASE, trn.tradeCurrency, trn.portfolio, position),
            position,
            trn.tradeAmount,
            trn.tradeBaseRate,
        )
        cashCost.value(
            currencyResolver.getMoneyValues(Position.In.PORTFOLIO, trn.tradeCurrency, trn.portfolio, position),
            position,
            trn.tradeAmount,
            trn.tradePortfolioRate,
        )
        cashCost.value(
            currencyResolver.getMoneyValues(Position.In.TRADE, trn.tradeCurrency, trn.portfolio, position),
            position,
            trn.tradeAmount,
            BigDecimal.ONE,
        ) // Trade to Cash Settlement ?
        return position
    }
}
