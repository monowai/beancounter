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
 * FX affects two position records.
 */
@Service
class FxBuyBehaviour(
    val currencyResolver: CurrencyResolver
) : AccumulationStrategy {
    private val cashCost = CashCost()

    override val supportedType: TrnType
        get() = TrnType.FX_BUY

    override fun accumulate(
        trn: Trn,
        positions: Positions,
        position: Position
    ): Position {
        position.quantityValues.purchased = position.quantityValues.purchased.add(trn.quantity)
        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.TRADE,
                trn.tradeCurrency,
                trn.portfolio,
                position
            ),
            position,
            trn.quantity,
            BigDecimal.ONE
        )
        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.BASE,
                trn.tradeCurrency,
                trn.portfolio,
                position
            ),
            position,
            trn.quantity,
            trn.tradeBaseRate
        )
        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.PORTFOLIO,
                trn.tradeCurrency,
                trn.portfolio,
                position
            ),
            position,
            trn.quantity,
            trn.tradePortfolioRate
        )

        if (trn.cashAsset != null) {
            handleCash(
                positions.getOrCreate(
                    trn.cashAsset!!,
                    trn.tradeDate
                ),
                trn
            )
        }

        return position
    }

    private fun handleCash(
        counterPosition: Position,
        trn: Trn
    ) {
        counterPosition.quantityValues.sold =
            counterPosition.quantityValues.sold.add(trn.cashAmount)
        // cashCost.value(
        //     currencyResolver.getMoneyValues(
        //         Position.In.TRADE,
        //         trn.cashCurrency!!,
        //         trn.portfolio,
        //         counterPosition
        //     ),
        //     counterPosition,
        //     trn.cashAmount,
        //     BigDecimal.ONE
        // )
        // // ToDo: Fix rates
        //
        // cashCost.value(
        //     currencyResolver.getMoneyValues(
        //         Position.In.BASE,
        //         trn.cashCurrency!!,
        //         trn.portfolio,
        //         counterPosition
        //     ),
        //     counterPosition,
        //     trn.cashAmount,
        //     trn.tradeBaseRate
        // )
        // cashCost.value(
        //     currencyResolver.getMoneyValues(
        //         Position.In.PORTFOLIO,
        //         trn.cashCurrency!!,
        //         trn.portfolio,
        //         counterPosition
        //     ),
        //     counterPosition,
        //     trn.cashAmount,
        //     trn.tradePortfolioRate
        // )
    }
}