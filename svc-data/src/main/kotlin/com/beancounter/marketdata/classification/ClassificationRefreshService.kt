package com.beancounter.marketdata.classification

import com.beancounter.common.contracts.BackfillResult
import com.beancounter.common.model.Asset
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service for refreshing asset classification data.
 * Handles both on-demand and scheduled refresh of sector/industry classifications.
 */
@Service
class ClassificationRefreshService(
    private val assetRepository: AssetRepository,
    private val assetFinder: AssetFinder,
    private val classificationEnricher: AlphaClassificationEnricher
) {
    private val log = LoggerFactory.getLogger(ClassificationRefreshService::class.java)

    /**
     * Refresh sector exposures for all ETF assets.
     * This re-fetches data from AlphaVantage even if exposures already exist.
     */
    fun refreshEtfSectors(): BackfillResult {
        log.info("Starting ETF sector refresh")
        val etfs = assetRepository.findActiveEtfs()
        return processAssets(etfs, "ETF sectors")
    }

    /**
     * Refresh classifications for all Equity assets.
     */
    fun refreshEquityClassifications(): BackfillResult {
        log.info("Starting Equity classification refresh")
        val equities = assetRepository.findActiveEquities()
        return processAssets(equities, "Equity classifications")
    }

    /**
     * Refresh classification for a single asset by ID.
     */
    fun refreshAsset(assetId: String): Boolean {
        val asset = assetRepository.findById(assetId)
        if (asset.isEmpty) {
            log.warn("Asset not found: $assetId")
            return false
        }

        val hydratedAsset = assetFinder.hydrateAsset(asset.get())
        return classificationEnricher.enrichClassification(hydratedAsset)
    }

    /**
     * Refresh classification for a single asset by market and code.
     */
    fun refreshAssetByCode(
        marketCode: String,
        assetCode: String
    ): Boolean {
        val asset = assetRepository.findByMarketCodeAndCode(marketCode, assetCode)
        if (asset.isEmpty) {
            log.warn("Asset not found: $marketCode:$assetCode")
            return false
        }

        val hydratedAsset = assetFinder.hydrateAsset(asset.get())
        return classificationEnricher.enrichClassification(hydratedAsset)
    }

    private fun processAssets(
        assets: List<Asset>,
        description: String
    ): BackfillResult {
        var processed = 0
        var errors = 0

        log.info("Processing ${assets.size} assets for $description")

        for (asset in assets) {
            try {
                val hydratedAsset = assetFinder.hydrateAsset(asset)
                if (classificationEnricher.enrichClassification(hydratedAsset)) {
                    processed++
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                // Continue processing other assets even if one fails
                errors++
                log.warn("Failed to refresh classification for ${asset.code}: ${e.message}")
            }
        }

        val result = BackfillResult(processed = processed, errors = errors, total = assets.size)
        log.info("$description refresh complete: $result")

        return result
    }
}