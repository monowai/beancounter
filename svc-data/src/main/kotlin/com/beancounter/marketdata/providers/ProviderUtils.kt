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
    private val marketService: MarketService,
) {
    fun splitProviders(assets: Collection<PriceAsset>): Map<MarketDataPriceProvider, MutableCollection<Asset>> =
        assets
            .filter { it.resolvedAsset?.status == Status.Active }
            .groupBy {
                it.resolvedAsset?.market ?: marketService.getMarket(it.market).also { market ->
                    it.resolvedAsset = Asset(code = it.code, market = market)
                }
            }.mapKeys { mdFactory.getMarketDataProvider(it.key) }
            .mapValues { it.value.map { priceAsset -> priceAsset.resolvedAsset!! }.toMutableList() }

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
