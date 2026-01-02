package com.beancounter.marketdata.assets

import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.markets.MarketService
import jakarta.transaction.Transactional
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.Locale
import java.util.stream.Stream

/**
 * Handles asset finding operations with built-in hydration.
 * This service can be injected and reused across the application.
 */
@Service
@Transactional
class AssetFinder(
    private val assetRepository: AssetRepository,
    private val marketService: MarketService,
    private val assetCategoryConfig: AssetCategoryConfig
) {
    /**
     * Find an asset by its ID.
     *
     * @param assetId the unique identifier of the asset
     * @return the found asset
     * @throws NotFoundException if the asset is not found
     */
    fun find(assetId: String): Asset =
        assetRepository
            .findById(assetId)
            .map { asset: Asset -> hydrateAsset(asset) }
            .orElseThrow { NotFoundException("Asset not found: $assetId") }

    /**
     * Find an asset locally by market code and asset code.
     *
     * @param assetInput the asset input containing market and code information
     * @return the found asset or null if not found
     */
    fun findLocally(assetInput: AssetInput): Asset? {
        val marketCode = assetInput.market.uppercase(Locale.getDefault())
        val code = assetInput.code

        // Search Local
        val market = marketService.getMarket(marketCode.uppercase())
        val findCode =
            if (market.code == PrivateMarketEnricher.ID) {
                PrivateMarketEnricher.parseCode(SystemUser(assetInput.owner), code)
            } else {
                code.uppercase(Locale.getDefault())
            }
        log.trace("Search for {}/{}", marketCode, code)

        return assetRepository
            .findByMarketCodeAndCode(
                marketCode.uppercase(Locale.getDefault()),
                findCode
            ).map { asset: Asset -> hydrateAsset(asset) }
            .orElse(null)
    }

    /**
     * Hydrate an asset by enriching it with market and category information.
     *
     * @param asset the asset to hydrate
     * @return the hydrated asset
     */
    fun hydrateAsset(asset: Asset): Asset {
        asset.market = marketService.getMarket(asset.marketCode)
        asset.assetCategory = assetCategoryConfig.get(asset.category) ?: assetCategoryConfig.get()!!
        return asset
    }

    /**
     * Find all assets by market code.
     *
     * @param marketCode the market code to search for
     * @return list of assets for the given market
     */
    fun findByMarketCode(marketCode: String): List<Asset> = assetRepository.findByMarketCode(marketCode)

    /**
     * Find all active assets for pricing.
     * Excludes inactive assets and those with empty codes.
     *
     * @return stream of active assets suitable for price refresh
     */
    fun findActiveAssetsForPricing(): Stream<Asset> = assetRepository.findActiveAssetsForPricing()

    companion object {
        private val log = LoggerFactory.getLogger(AssetFinder::class.java)
    }
}