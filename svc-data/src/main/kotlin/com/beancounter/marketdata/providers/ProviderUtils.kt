package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import com.beancounter.marketdata.markets.MarketService
import org.springframework.stereotype.Service

/**
 * General helper functions common across data providers.
 */
@Service
class ProviderUtils(
    private val mdFactory: MdFactory,
    private val marketService: MarketService
) {
    fun splitProviders(priceAssets: Collection<PriceAsset>): Map<MarketDataPriceProvider, MutableCollection<Asset>> {
        val mdpAssetResults: MutableMap<MarketDataPriceProvider, MutableCollection<Asset>> =
            mutableMapOf()
        priceAssets.forEach { priceAsset ->
            val market =
                priceAsset.resolvedAsset?.market ?: marketService
                    .getMarket(priceAsset.market)
                    .also { market ->
                        priceAsset.resolvedAsset =
                            Asset(
                                code = priceAsset.code,
                                market = market
                            )
                    }
            val marketDataProvider = mdFactory.getMarketDataProvider(market)

            if (priceAsset.resolvedAsset!!.status == Status.Active) {
                val mdpAssets = mdpAssetResults.getOrPut(marketDataProvider) { mutableListOf() }
                mdpAssets.add(priceAsset.resolvedAsset!!)
            }
        }
        return mdpAssetResults
    }

    fun getInputs(apiAssets: Collection<Asset>): List<PriceAsset> {
        val results: MutableList<PriceAsset> = ArrayList()
        apiAssets.forEach { asset ->
            if (asset.status == Status.Active) {
                results.add(PriceAsset(asset))
            }
        }
        return results
    }
}