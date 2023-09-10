package com.beancounter.common

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import java.math.BigDecimal

/**
 * Used to reduce duplicate object code quality warnings.
 */
class Constants {
    companion object {
        val SGD = Currency("SGD")
        val NYSE = Market("NYSE")
        const val integrationErrorMsg = "Integration Error"
        const val test = "test"
        const val testUri = "/$test"
        const val message = "message"
        const val openProp = "open"
        const val closeProp = "close"
        const val previousCloseProp = "previousClose"
        const val changeProp = "change"
        const val changePercentProp = "changePercent"
        val two = BigDecimal("2.0")
        val one = BigDecimal("1.00")
        const val oneString = "1"
    }
}
