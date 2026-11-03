package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.TrnType
import org.springframework.stereotype.Service

/**
 * Accumulates an expense transaction for real estate/private assets.
 * Tracks expenses against the asset, as dividends track dividend income.
 */
@Service
class ExpenseBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver()
) : AmountBehaviour(currencyResolver, MoneyValues::expenses) {
    override val supportedType: TrnType
        get() = TrnType.EXPENSE
}