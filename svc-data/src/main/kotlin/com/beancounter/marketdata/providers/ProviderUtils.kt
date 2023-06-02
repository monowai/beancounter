package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.Status
import com.beancounter.marketdata.markets.MarketService
import org.springframework.stereotype.Service

/**
 * General helper functions common across data providers.
 */
@Service
class ProviderUtils(private val mdFactory: MdFactory, private val marketService: MarketService) {
    fun splitProviders(assets: Collection<PriceAsset>): Map<MarketDataPriceProvider, MutableCollection<Asset>> {
        val mdpAssetResults: MutableMap<MarketDataPriceProvider, MutableCollection<Asset>> = mutableMapOf()
        for (input in assets) {
            var market: Market
            if (input.resolvedAsset != null) {
                market = input.resolvedAsset!!.market
            } else {
                market = marketService.getMarket(input.market)
                val resolvedAsset = Asset(code = input.code, market)
                input.resolvedAsset = resolvedAsset
            }
            val marketDataProvider = mdFactory.getMarketDataProvider(market)
            var mdpAssets = mdpAssetResults[marketDataProvider]
            if (mdpAssets == null) {
                mdpAssets = mutableListOf()
                mdpAssetResults[marketDataProvider] = mdpAssets
            }
            if (input.resolvedAsset!!.status == Status.Active) {
                mdpAssets.add(input.resolvedAsset!!)
            }
        }
        return mdpAssetResults
    }

    fun getInputs(apiAssets: Collection<Asset>): Collection<PriceAsset> {
        val results: MutableCollection<PriceAsset> = ArrayList()
        apiAssets.forEach { asset ->
            if (asset.status == Status.Active) {
                results.add(PriceAsset(asset))
            }
        }
        return results
    }
}
