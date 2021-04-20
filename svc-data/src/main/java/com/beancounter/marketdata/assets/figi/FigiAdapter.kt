package com.beancounter.marketdata.assets.figi

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.springframework.stereotype.Service
/**
 * Bloomberg OpenFigi support for asset enrichment.
 */
@Service
class FigiAdapter {
    fun transform(market: Market, assetCode: String): Asset {
        return Asset(
            assetCode,
            assetCode,
            assetCode,
            "Equity",
            market,
            market.code,
            assetCode
        )
    }

    fun transform(market: Market, assetCode: String, figiAsset: FigiAsset?): Asset {
        val asset = transform(market, assetCode)
        asset.name = figiAsset!!.name
        asset.category = figiAsset.securityType2
        return asset
    }
}
