package com.beancounter.position.utils

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position

/**
 * Positions compute values in various currency buckets.  This class resolves the currency object bucket.
 */
class CurrencyResolver {
    fun resolve(`in`: Position.In, portfolio: Portfolio, tradeCurrency: Currency): Currency {
        return when (`in`) {
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
    }
}
