package com.beancounter.position.accumulation

import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.TrnType
import org.springframework.stereotype.Service

/**
 * Accumulates an income transaction (interest, salary, etc.) into a position.
 * Tracks income in the dividends (income) column, like DividendBehaviour.
 */
@Service
class IncomeBehaviour(
    currencyResolver: CurrencyResolver = CurrencyResolver()
) : AmountBehaviour(currencyResolver, MoneyValues::dividends) {
    override val supportedType: TrnType
        get() = TrnType.INCOME
}