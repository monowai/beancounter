package com.beancounter.position.accumulation

import com.beancounter.common.model.Position
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.position.utils.CurrencyResolver
import com.beancounter.position.valuation.CashCost
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Generic cash accumulation behaviour.
 *
 * These values are unreliable and need to be refactored.
 */
@Service
class CashAccumulator(
    val currencyResolver: CurrencyResolver
) {
    private val cashCost = CashCost()

    fun accumulate(
        cashPosition: Position,
        position: Position,
        quantity: BigDecimal,
        trn: Trn
    ): Position {
        if (TrnType.isCashCredited(trn.trnType)) {
            cashPosition.quantityValues.purchased = position.quantityValues.purchased.add(quantity)
        } else {
            cashPosition.quantityValues.sold = position.quantityValues.sold.add(quantity)
        }

        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.TRADE,
                trn.cashCurrency!!,
                trn.portfolio,
                position
            ),
            cashPosition,
            quantity,
            BigDecimal.ONE,
            trn.trnType
        ) // Cash trade currency
        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.BASE,
                trn.cashCurrency!!,
                trn.portfolio,
                position
            ),
            cashPosition,
            quantity,
            trn.tradeBaseRate,
            trn.trnType
        )
        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.PORTFOLIO,
                trn.cashCurrency!!,
                trn.portfolio,
                position
            ),
            cashPosition,
            quantity,
            trn.tradePortfolioRate,
            trn.trnType
        )
        return position
    }
}