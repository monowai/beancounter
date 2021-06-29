package com.beancounter.marketdata.providers

import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.service.MarketDataProvider
import com.beancounter.marketdata.service.MdFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * General helper functions common across data providers.
 */
@Service
class ProviderUtils @Autowired constructor(private val mdFactory: MdFactory, private val marketService: MarketService) {
    fun splitProviders(assets: Collection<AssetInput>): Map<MarketDataProvider, MutableCollection<Asset>> {
        val mdpAssetResults: MutableMap<MarketDataProvider, MutableCollection<Asset>> = HashMap()
        for (input in assets) {
            var market: Market
            if (input.resolvedAsset != null) {
                market = input.resolvedAsset!!.market
            } else {
                market = marketService.getMarket(input.market)
                val resolvedAsset = Asset(input, market)
                input.resolvedAsset = resolvedAsset
            }
            val marketDataProvider = mdFactory.getMarketDataProvider(market)
            if (marketDataProvider != null) {
                var mdpAssets = mdpAssetResults[marketDataProvider]
                if (mdpAssets == null) {
                    mdpAssets = ArrayList()
                    mdpAssetResults[marketDataProvider] = mdpAssets
                }
                mdpAssets.add(input.resolvedAsset!!)
            }
        }
        return mdpAssetResults
    }

    fun getInputs(apiAssets: MutableCollection<Asset>?): Collection<AssetInput> {
        val results: MutableCollection<AssetInput> = ArrayList()
        for (apiAsset in apiAssets!!) {
            results.add(AssetInput(apiAsset.market.code, apiAsset.code, apiAsset))
        }
        return results
    }
}
