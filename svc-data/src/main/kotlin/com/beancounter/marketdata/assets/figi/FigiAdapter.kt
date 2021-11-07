package com.beancounter.marketdata.assets.figi

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import org.springframework.stereotype.Service

/**
 * Bloomberg OpenFigi support for asset enrichment.
 */
@Service
class FigiAdapter {
    fun transform(market: Market, assetCode: String, defaultName: String? = null): Asset {
        return Asset(
            id = assetCode,
            code = assetCode,
            name = defaultName,
            category = "Equity",
            market = market,
            marketCode = market.code,
            priceSymbol = assetCode
        )
    }

    fun transform(market: Market, assetCode: String, figiAsset: FigiAsset): Asset {
        val asset = transform(market, assetCode, defaultName = figiAsset.name)
        asset.name = figiAsset.name
        asset.category = figiAsset.securityType2
        return asset
    }
}
