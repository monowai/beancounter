package com.beancounter.position

import com.beancounter.common.model.Currency
import java.math.BigDecimal

/**
 * Used to reduce duplicate object code quality warnings.
 */
class Constants {
    companion object {
        val fourK = BigDecimal("4000.00")

        val twoK = BigDecimal("2000.00")

        val ten = BigDecimal("10.00")

        val twenty = BigDecimal("20.00")

        val hundred = BigDecimal(100)

        val USD = Currency("USD")
        val NZD = Currency("NZD")
        val GBP = Currency("GBP")
        val SGD = Currency("SGD")
    }
}
