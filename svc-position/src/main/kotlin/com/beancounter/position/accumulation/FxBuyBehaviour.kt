package com.beancounter.position.accumulation

import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.Trn
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * FX affects two position records.
 */
@Service
class FxBuyBehaviour : AccumulationStrategy {
    private val cashCost = CashCost()
    override fun accumulate(trn: Trn, positions: Positions, position: Position, portfolio: Portfolio): Position {
        position.quantityValues.purchased = position.quantityValues.purchased.add(trn.quantity)
        cashCost.value(
            trn.tradeCurrency,
            trn.quantity,
            portfolio,
            position,
            Position.In.TRADE,
            BigDecimal.ONE
        )
        cashCost.value(
            trn.tradeCurrency,
            trn.quantity,
            portfolio,
            position,
            Position.In.BASE,
            trn.tradeBaseRate
        )
        cashCost.value(
            trn.tradeCurrency,
            trn.quantity,
            portfolio,
            position,
            Position.In.PORTFOLIO,
            trn.tradePortfolioRate
        )

        if (trn.cashAsset != null) {
            handleCash(positions[trn.cashAsset!!, trn.tradeDate], trn, portfolio)
        }

        return position
    }

    private fun handleCash(
        counterPosition: Position,
        trn: Trn,
        portfolio: Portfolio
    ) {
        counterPosition.quantityValues.sold = counterPosition.quantityValues.sold.add(trn.cashAmount)
        cashCost.value(
            trn.cashCurrency!!,
            trn.cashAmount,
            portfolio,
            counterPosition,
            Position.In.TRADE,
            BigDecimal.ONE
        )
        // ToDo: Fix rates

        cashCost.value(
            trn.cashCurrency!!,
            trn.cashAmount,
            portfolio,
            counterPosition,
            Position.In.BASE,
            trn.tradeBaseRate
        )
        cashCost.value(
            trn.cashCurrency!!,
            trn.cashAmount,
            portfolio,
            counterPosition,
            Position.In.PORTFOLIO,
            trn.tradePortfolioRate
        )
    }
}
