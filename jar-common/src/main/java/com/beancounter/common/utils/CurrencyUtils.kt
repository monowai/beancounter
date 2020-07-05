package com.beancounter.common.utils

import com.beancounter.common.model.Currency
import com.beancounter.common.model.IsoCurrencyPair
import java.math.BigDecimal

class CurrencyUtils {
    fun getCurrency(isoCode: String): Currency {
        return Currency(isoCode, null, null)
    }

    fun getCurrencyPair(rate: BigDecimal?, from: Currency, to: Currency): IsoCurrencyPair? {
        if (rate == null && !from.code.equals(to.code, ignoreCase = true)) {
            return IsoCurrencyPair(from = from.code, to = to.code)
        }
        return null
    }
}