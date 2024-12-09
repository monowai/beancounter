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
        const val INT_ERROR = "Integration Error"
        const val TEST = "test"
        const val TEST_UR = "/$TEST"
        const val MESSAGE = "message"
        const val P_OPEN = "open"
        const val P_CLOSE = "close"
        const val P_PREVIOUS_CLOSE = "previousClose"
        const val P_CHANGE = "change"
        const val P_CHANGE_PERCENT = "changePercent"
        val two = BigDecimal("2.0")
        val one = BigDecimal("1.00")
        const val ONE = "1"
    }
}