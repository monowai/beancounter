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
        // Enforce sign based on transaction type - credits are positive, debits are negative
        // This ensures getTotal() (purchased + sold) calculates correctly regardless of input sign
        val signedQuantity =
            if (TrnType.isCashCredited(trn.trnType)) {
                quantity.abs()
            } else {
                quantity.abs().negate()
            }

        if (TrnType.isCashCredited(trn.trnType)) {
            cashPosition.quantityValues.purchased = position.quantityValues.purchased.add(signedQuantity)
        } else {
            cashPosition.quantityValues.sold = position.quantityValues.sold.add(signedQuantity)
        }

        // For cash transactions (DEPOSIT, WITHDRAWAL), the currency comes from the trade itself
        // since the asset IS the cash. For other transactions, use cashCurrency.
        val effectiveCashCurrency = trn.cashCurrency ?: trn.tradeCurrency

        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.TRADE,
                effectiveCashCurrency,
                trn.portfolio,
                position
            ),
            cashPosition,
            signedQuantity,
            BigDecimal.ONE
        ) // Cash trade currency
        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.BASE,
                effectiveCashCurrency,
                trn.portfolio,
                position
            ),
            cashPosition,
            signedQuantity,
            trn.tradeBaseRate
        )
        cashCost.value(
            currencyResolver.getMoneyValues(
                Position.In.PORTFOLIO,
                effectiveCashCurrency,
                trn.portfolio,
                position
            ),
            cashPosition,
            signedQuantity,
            trn.tradePortfolioRate
        )
        return position
    }
}