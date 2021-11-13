package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market

/**
 * Test asset enricher
 */
class MockEnricher : AssetEnricher {
    override fun enrich(id: String, market: Market, code: String, defaultName: String?): Asset? {
        return if (code.equals("BLAH", ignoreCase = true)) {
            null
        } else Asset(
            id = id,
            code = code,
            name = defaultName?.replace("\"", ""),
            category = "Equity",
            market = market,
            marketCode = market.code,
            priceSymbol = code
        )
    }

    override fun canEnrich(asset: Asset): Boolean {
        return asset.name == null
    }

    override fun id(): String {
        return "MOCK"
    }
}
