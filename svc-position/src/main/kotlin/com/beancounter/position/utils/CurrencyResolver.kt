package com.beancounter.position.utils

import com.beancounter.common.model.Currency
import com.beancounter.common.model.MoneyValues
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import org.springframework.stereotype.Service

/**
 * Positions compute values in various currency buckets.  This class resolves the currency object bucket.
 */
@Service
class CurrencyResolver {
    fun resolve(
        `in`: Position.In,
        portfolio: Portfolio,
        tradeCurrency: Currency,
    ): Currency =
        when (`in`) {
            Position.In.TRADE -> {
                tradeCurrency
            }

            Position.In.PORTFOLIO -> {
                portfolio.currency
            }

            else -> {
                portfolio.base
            }
        }

    fun getMoneyValues(
        `in`: Position.In,
        currency: Currency,
        portfolio: Portfolio,
        position: Position,
    ): MoneyValues =
        position.getMoneyValues(
            `in`,
            resolve(
                `in`,
                portfolio,
                currency,
            ),
        )
}
