package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PriceAsset
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Status
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * General helper functions common across data providers.
 */
@Service
class ProviderUtils(
    private val mdFactory: MdFactory
) {
    private val log = LoggerFactory.getLogger(ProviderUtils::class.java)

    /**
     * Groups resolved assets by their data provider. Callers MUST pre-resolve
     * `priceAsset.resolvedAsset` (see [com.beancounter.marketdata.assets.AssetService.resolveAssets]).
     * Unresolved entries are skipped with a warning — the previous behaviour
     * of fabricating `Asset(code, market)` produced a phantom row with
     * `id = code` that crashed the async persistence path when no matching
     * DB row existed (DATA-4G).
     */
    fun splitProviders(priceAssets: Collection<PriceAsset>): Map<MarketDataPriceProvider, MutableCollection<Asset>> {
        val mdpAssetResults: MutableMap<MarketDataPriceProvider, MutableCollection<Asset>> =
            mutableMapOf()
        priceAssets.forEach { priceAsset ->
            val resolved = priceAsset.resolvedAsset
            if (resolved == null) {
                log.warn(
                    "Skipping price lookup for unresolved asset: market={}, code={}",
                    priceAsset.market,
                    priceAsset.code
                )
                return@forEach
            }
            if (resolved.status != Status.Active) return@forEach
            val marketDataProvider = mdFactory.getMarketDataProvider(resolved.market)
            mdpAssetResults.getOrPut(marketDataProvider) { mutableListOf() }.add(resolved)
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