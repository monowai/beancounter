package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaEtfProfileResponse
import com.beancounter.marketdata.providers.alpha.AlphaOverviewResponse
import com.beancounter.marketdata.providers.alpha.AlphaProxy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Enriches assets with classification data from AlphaVantage.
 *
 * - Equities: Uses OVERVIEW endpoint to get Sector and Industry
 * - ETFs: Uses ETF_PROFILE endpoint to get sector weights
 */
@Service
class AlphaClassificationEnricher(
    private val alphaConfig: AlphaConfig,
    private val alphaProxy: AlphaProxy,
    private val classificationService: ClassificationService
) {
    private val log = LoggerFactory.getLogger(AlphaClassificationEnricher::class.java)
    private val objectMapper = alphaConfig.getObjectMapper()

    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    /**
     * Check if this asset can be enriched with classification data.
     */
    fun canEnrich(asset: Asset): Boolean {
        val category = asset.category.uppercase()
        return category in EQUITY_CATEGORIES || category in ETF_CATEGORIES
    }

    /**
     * Check if this asset is an ETF.
     */
    fun isEtf(asset: Asset): Boolean = asset.category.uppercase() in ETF_CATEGORIES

    /**
     * Check if this asset is an Equity.
     */
    fun isEquity(asset: Asset): Boolean = asset.category.uppercase() in EQUITY_CATEGORIES

    /**
     * Enrich an asset with classification data.
     * Returns true if enrichment was successful.
     */
    fun enrichClassification(asset: Asset): Boolean =
        try {
            when {
                isEquity(asset) -> enrichEquity(asset)
                isEtf(asset) -> enrichEtf(asset)
                else -> false
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            // Gracefully handle API/parsing failures without propagating
            log.warn("Failed to enrich classification for ${asset.code}: ${e.message}")
            false
        }

    private fun enrichEquity(asset: Asset): Boolean {
        val symbol = alphaConfig.getPriceCode(asset)
        val response = alphaProxy.getOverview(symbol, apiKey)

        if (response.isBlank() || response.contains("Error")) {
            log.debug("No overview data for $symbol")
            return false
        }

        val overview = objectMapper.readValue(response, AlphaOverviewResponse::class.java)

        if (overview.sector.isNullOrBlank()) {
            log.debug("No sector in overview for $symbol")
            return false
        }

        val standard = classificationService.getAlphaStandard()

        // Create sector classification - rawCode from API is normalized to display name
        val sectorItem =
            classificationService.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                rawCode = overview.sector
            )

        classificationService.classifyAsset(
            asset = asset,
            standard = standard,
            item = sectorItem,
            level = ClassificationLevel.SECTOR,
            source = AssetClassification.SOURCE_ALPHA_OVERVIEW
        )

        // Create industry classification if available
        if (!overview.industry.isNullOrBlank()) {
            val industryItem =
                classificationService.getOrCreateItem(
                    standard = standard,
                    level = ClassificationLevel.INDUSTRY,
                    name = overview.industry,
                    parent = sectorItem
                )

            classificationService.classifyAsset(
                asset = asset,
                standard = standard,
                item = industryItem,
                level = ClassificationLevel.INDUSTRY,
                source = AssetClassification.SOURCE_ALPHA_OVERVIEW
            )
        }

        log.info("Classified ${asset.code} as ${overview.sector} / ${overview.industry ?: "N/A"}")
        return true
    }

    private fun enrichEtf(asset: Asset): Boolean {
        val symbol = alphaConfig.getPriceCode(asset)
        val response = alphaProxy.getEtfProfile(symbol, apiKey)

        if (response.isBlank() || response.contains("Error")) {
            log.debug("No ETF profile data for $symbol")
            return false
        }

        val profile = objectMapper.readValue(response, AlphaEtfProfileResponse::class.java)

        val hasSectors = !profile.sectors.isNullOrEmpty()
        val hasHoldings = !profile.holdings.isNullOrEmpty()

        if (!hasSectors && !hasHoldings) {
            log.debug("No sector allocations or holdings in ETF profile for $symbol")
            return false
        }

        val standard = classificationService.getAlphaStandard()

        // Process sector exposures
        var sectorCount = 0
        if (hasSectors) {
            classificationService.clearExposures(asset.id)

            for (sectorData in profile.sectors) {
                val weight = sectorData.weight.toBigDecimalOrNull()
                if (weight == null || weight.signum() <= 0) {
                    continue
                }

                val sectorItem =
                    classificationService.getOrCreateItem(
                        standard = standard,
                        level = ClassificationLevel.SECTOR,
                        rawCode = sectorData.sector
                    )

                classificationService.addExposure(
                    asset = asset,
                    standard = standard,
                    item = sectorItem,
                    weight = weight
                )
                sectorCount++
            }
        }

        // Process top 10 holdings
        var holdingCount = 0
        if (hasHoldings) {
            classificationService.clearHoldings(asset.id)

            for (holdingData in profile.holdings.take(MAX_HOLDINGS)) {
                val weight = holdingData.weight?.toBigDecimalOrNull()
                if (weight == null || weight.signum() <= 0 || holdingData.symbol.isNullOrBlank()) {
                    continue
                }

                classificationService.addHolding(
                    asset = asset,
                    symbol = holdingData.symbol,
                    name = holdingData.description,
                    weight = weight
                )
                holdingCount++
            }
        }

        log.info("Added $sectorCount sector exposures and $holdingCount holdings for ${asset.code}")
        return sectorCount > 0 || holdingCount > 0
    }

    companion object {
        private val EQUITY_CATEGORIES = setOf("EQUITY", "COMMON STOCK")
        private val ETF_CATEGORIES = setOf("ETF", "EXCHANGE TRADED FUND")
        private const val MAX_HOLDINGS = 10
    }
}