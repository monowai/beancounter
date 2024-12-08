package com.beancounter.marketdata.assets

import com.beancounter.common.model.Asset
import com.beancounter.marketdata.markets.MarketService
import org.springframework.stereotype.Service

/**
 * Enrich the persistent asset object with objects managed via configuration properties.
 */
@Service
class AssetHydrationService(
    val marketService: MarketService,
    val assetCategoryConfig: AssetCategoryConfig,
) {
    fun hydrateAsset(asset: Asset): Asset {
        asset.market = marketService.getMarket(asset.marketCode)
        asset.assetCategory = assetCategoryConfig.get(asset.category)!!
        return asset
    }
}
