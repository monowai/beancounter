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

        val counterPosition = positions[trn.cashAsset, trn.tradeDate]
        counterPosition.quantityValues.sold = counterPosition.quantityValues.sold.add(trn.cashAmount)
        cashCost.value(
            trn.cashCurrency!!,
            trn.cashAmount!!,
            portfolio,
            counterPosition,
            Position.In.TRADE,
            BigDecimal.ONE
        )
        // ToDo: Fix rates

        cashCost.value(
            trn.cashCurrency!!,
            trn.cashAmount!!,
            portfolio,
            counterPosition,
            Position.In.BASE,
            trn.tradeBaseRate
        )
        cashCost.value(
            trn.cashCurrency!!,
            trn.cashAmount!!,
            portfolio,
            counterPosition,
            Position.In.PORTFOLIO,
            trn.tradePortfolioRate
        )

        return position
    }

    //        val cashToBase = IsoCurrencyPair(position.asset.priceSymbol!!, portfolio.base.code)
//        val cashToPortfolio = IsoCurrencyPair(position.asset.priceSymbol!!, portfolio.currency.code)
//        val fxResponse = fxService.getRates(
//            fxRequest = FxRequest(
//                trn.tradeDate.toString(),
//                arrayListOf(
//                    cashToBase,
//                    cashToPortfolio
//                )
//            )
//        )
//        value(trn, portfolio, position, Position.In.BASE, fxResponse.data.rates[cashToBase]?.rate)
//        value(trn, portfolio, position, Position.In.PORTFOLIO, fxResponse.data.rates[cashToPortfolio]?.rate)
}
