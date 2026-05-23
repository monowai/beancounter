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
            ).orElse(null)
    }

    /**
     * Hydrate an asset by enriching it with market and category information.
     *
     * Kept for the rare case of an Asset constructed in-process (outside
     * JPA) — anything coming out of a repository is already hydrated by
     * [com.beancounter.marketdata.assets.AssetEntityListener]'s `@PostLoad`
     * hook. Prefer the listener; this method exists for callers that mint
     * a fresh `Asset` from a non-DB source.
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
     * @return list of active assets suitable for price refresh
     */
    fun findActiveAssetsForPricing(): List<Asset> = assetRepository.findActiveAssetsForPricing().use { it.toList() }

    /**
     * Find assets the scheduled price refresh should actually fetch:
     * active, non-private, AND held by at least one user (positive net
     * BUY/ADD - SELL/REDUCE quantity). Skips orphans — assets sold-out,
     * never bought, or never traded — to keep provider quota and Sentry
     * noise focused on tickers people still own.
     */
    fun findHeldAssetsForPricing(): List<Asset> = assetRepository.findHeldAssetsForPricing().use { it.toList() }

    /**
     * Index benchmark assets refreshed on schedule regardless of holdings.
     */
    fun findActiveIndexAssets(): List<Asset> = assetRepository.findActiveIndexAssets()

    companion object {
        private val log = LoggerFactory.getLogger(AssetFinder::class.java)
    }
}