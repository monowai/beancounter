package com.beancounter.shell

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market

/**
 * Used to reduce duplicate object code quality warnings.
 */
class Constants {
    companion object {
        val USD = Currency("USD")
        val NZD = Currency("NZD")
        val GBP = Currency("GBP")
        val MOCK = Market("MOCK")
    }
}