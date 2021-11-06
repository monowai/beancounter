package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.springframework.stereotype.Service

/**
 * For assets that will never be updated by an external provider, i.e. Cash, Custom, Property etc.
 */
@Service
class EchoEnricher : AssetEnricher {
    override fun enrich(market: Market, code: String, defaultName: String?): Asset? {
        return null
    }

    override fun canEnrich(asset: Asset): Boolean {
        return asset.market.type == "Internal"
    }

    override fun id(): String {
        return "ECHO"
    }
}
