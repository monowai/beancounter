package com.beancounter.marketdata

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getAsset

/**
 * Used to reduce duplicate object code quality warnings.
 */
class Constants {
    companion object {
        const val BLANK = ""
        const val usdValue = "USD"
        val USD = Currency(usdValue)
        val NASDAQ = Market("NASDAQ")
        val NYSE = Market("NYSE")

        val AAPL = getAsset(NASDAQ, "AAPL")
        val MSFT = getAsset(NASDAQ, "MSFT")
        val AMP = getAsset("ASX", "AMP")
        val SGD = Currency("SGD")
        val AUD = Currency("AUD")
        val ASX = Market("ASX", AUD)
        val GBP = Currency(code = "GBP", symbol = "￡")

        val EUR = Currency("EUR", symbol = "€")

        val NZD = Currency("NZD")
        val NZX = Market("NZX", NZD)
    }
}
