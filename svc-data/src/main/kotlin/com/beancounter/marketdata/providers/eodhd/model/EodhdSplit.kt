package com.beancounter.marketdata.providers.eodhd.model

import java.math.BigDecimal
import java.math.MathContext
import java.time.LocalDate

/**
 * EODHD split row from `/api/splits/{symbol}`.
 *
 * EODHD encodes the ratio as a `"new/old"` string, e.g. `"4.000000/1.000000"` for AAPL's 2020 4-for-1
 * split. [factor] parses that into the multiplicative factor BeanCounter stores on
 * [com.beancounter.common.event.CorporateEvent.split].
 */
data class EodhdSplit(
    val date: LocalDate,
    val split: String
) {
    /**
     * Returns the multiplicative split factor (numerator / denominator).
     *
     * Examples: `"4/1"` → 4.0, `"1/10"` → 0.1, `"3/2"` → 1.5.
     */
    fun factor(): BigDecimal {
        val parts = split.split("/")
        require(parts.size == 2) { "Malformed EODHD split string: '$split'" }
        val numerator = BigDecimal(parts[0].trim())
        val denominator = BigDecimal(parts[1].trim())
        return numerator.divide(denominator, MathContext.DECIMAL64)
    }
}