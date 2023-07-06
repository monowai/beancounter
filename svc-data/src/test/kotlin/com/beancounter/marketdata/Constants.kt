package com.beancounter.marketdata

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.AssetUtils
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset

/**
 * Centralise constant values. Reduces duplicate object code quality warnings.
 */
class Constants {
    companion object {
        const val pName = "name"
        const val pCode = "code"

        val USD = Currency("USD")
        val NASDAQ = Market("NASDAQ")
        val US = Market("US")
        val CUSTOM = Market("CUSTOM") // Echo enricher to support mocking scenarios
        val CASH = Market("CASH")
        val NYSE = Market("NYSE")
        val ASX = Market("ASX")

        val AAPL = getTestAsset(NASDAQ, "AAPL")
        val MSFT = getTestAsset(NASDAQ, "MSFT")

        val AMP = getTestAsset(ASX, "AMP")
        val SGD = Currency("SGD")
        val AUD = Currency("AUD")
        val GBP = Currency(code = "GBP", symbol = "￡")
        val EUR = Currency("EUR", symbol = "€")
        val NZD = Currency("NZD")

        val NZX = Market("NZX", NZD.code)
        val systemUser = SystemUser("auth0|user", "user@testing.com", auth0 = "auth0")

        val msftInput = AssetUtils.getAssetInput(NASDAQ.code, MSFT.code)
        val aaplInput = AssetUtils.getAssetInput(NASDAQ.code, AAPL.code)

        val nzdCashBalance = Asset(
            code = NZD.code,
            id = NZD.code,
            name = "${NZD.code} Balance",
            market = Market("CASH", NZD.code),
            priceSymbol = NZD.code,
            category = "CASH",
            assetCategory = AssetCategory("CASH", "Cash"),
        )
        val usdCashBalance = Asset(
            code = USD.code,
            id = USD.code,
            name = "${USD.code} Balance",
            market = Market("CASH", USD.code),
            priceSymbol = USD.code,
            category = "CASH",
            assetCategory = AssetCategory("CASH", "Cash"),
        )
    }
}
