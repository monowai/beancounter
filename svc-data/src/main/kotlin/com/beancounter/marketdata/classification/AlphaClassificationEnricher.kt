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
) : ClassificationEnricher {
    private val log = LoggerFactory.getLogger(AlphaClassificationEnricher::class.java)
    private val objectMapper = alphaConfig.getObjectMapper()

    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    /**
     * Check if this asset can be enriched with classification data.
     */
    override fun canEnrich(asset: Asset): Boolean {
        val category = asset.category.uppercase()
        return category in EQUITY_CATEGORIES || category in ETF_CATEGORIES
    }

    /**
     * Check if this asset is an ETF.
     */
    override fun isEtf(asset: Asset): Boolean = asset.category.uppercase() in ETF_CATEGORIES

    /**
     * Check if this asset is an Equity.
     */
    override fun isEquity(asset: Asset): Boolean = asset.category.uppercase() in EQUITY_CATEGORIES

    /**
     * Enrich an asset with classification data. See [EnrichmentResult].
     */
    override fun enrichClassification(asset: Asset): EnrichmentResult =
        try {
            when {
                isEquity(asset) -> enrichEquity(asset)
                isEtf(asset) -> enrichEtf(asset)
                else -> EnrichmentResult.NO_DATA
            }
        } catch (
            @Suppress("TooGenericExceptionCaught")
            e: Exception
        ) {
            // Gracefully handle API/parsing failures without propagating
            log.warn("Failed to enrich classification for ${asset.code}: ${e.message}")
            EnrichmentResult.FAILED
        }

    private fun enrichEquity(asset: Asset): EnrichmentResult {
        val symbol = alphaConfig.getPriceCode(asset)
        val response = alphaProxy.getOverview(symbol, apiKey)

        if (isRateLimited(response)) {
            log.warn("AlphaVantage rate limit hit while enriching $symbol")
            return EnrichmentResult.RATE_LIMITED
        }

        if (response.isBlank() || response.contains("Error")) {
            log.debug("No overview data for $symbol")
            return EnrichmentResult.FAILED
        }

        val overview = objectMapper.readValue(response, AlphaOverviewResponse::class.java)

        if (overview.sector.isNullOrBlank()) {
            log.debug("No sector in overview for $symbol")
            return EnrichmentResult.NO_DATA
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
        return EnrichmentResult.ENRICHED
    }

    private fun enrichEtf(asset: Asset): EnrichmentResult {
        val symbol = alphaConfig.getPriceCode(asset)
        val response = alphaProxy.getEtfProfile(symbol, apiKey)

        if (isRateLimited(response)) {
            log.warn("AlphaVantage rate limit hit while enriching $symbol")
            return EnrichmentResult.RATE_LIMITED
        }

        if (response.isBlank() || response.contains("Error")) {
            log.debug("No ETF profile data for $symbol")
            return EnrichmentResult.FAILED
        }

        val profile = objectMapper.readValue(response, AlphaEtfProfileResponse::class.java)

        val hasSectors = !profile.sectors.isNullOrEmpty()
        val hasHoldings = !profile.holdings.isNullOrEmpty()

        if (!hasSectors && !hasHoldings) {
            log.debug("No sector allocations or holdings in ETF profile for $symbol")
            return EnrichmentResult.NO_DATA
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

        // Process top holdings - skip placeholder symbols (AlphaVantage returns literal "n/a"
        // for unlisted positions) and de-duplicate within the batch, since two identical
        // symbols for the same parent asset violate the asset_holding unique constraint and
        // roll back the whole enrichment (including the sector exposures above).
        var holdingCount = 0
        if (hasHoldings) {
            classificationService.clearHoldings(asset.id)

            val seenSymbols = mutableSetOf<String>()
            for (holdingData in profile.holdings) {
                if (holdingCount >= MAX_HOLDINGS) {
                    break
                }

                val weight = holdingData.weight?.toBigDecimalOrNull()
                val rawSymbol = holdingData.symbol?.trim()
                if (weight == null || weight.signum() <= 0 || rawSymbol.isNullOrBlank()) {
                    continue
                }

                val normalizedSymbol = rawSymbol.uppercase()
                if (normalizedSymbol in PLACEHOLDER_SYMBOLS || !seenSymbols.add(normalizedSymbol)) {
                    continue
                }

                classificationService.addHolding(
                    asset = asset,
                    symbol = rawSymbol,
                    name = holdingData.description,
                    weight = weight
                )
                holdingCount++
            }
        }

        log.info("Added $sectorCount sector exposures and $holdingCount holdings for ${asset.code}")
        return if (sectorCount > 0 || holdingCount > 0) EnrichmentResult.ENRICHED else EnrichmentResult.NO_DATA
    }

    /**
     * AlphaVantage signals quota exhaustion with a top-level `Information` or `Note` key returned
     * over HTTP 200 - not the usual "Error Message" body. Must be checked before parsing: such a
     * body deserializes cleanly into an empty response and would otherwise be indistinguishable
     * from [EnrichmentResult.NO_DATA], which is precisely the bug that let a whole refresh run
     * silently no-op.
     *
     * The wording is not stable - observed variants include "standard API rate limit is 25
     * requests per day", "lift the free key rate limit", and "our standard API call frequency is
     * 5 calls per minute". Rather than chase phrasing, treat the presence of either key as
     * disqualifying: neither ever appears in a valid OVERVIEW or ETF_PROFILE payload. An invalid
     * API key also lands here, and aborting the run is the right response to that too.
     */
    private fun isRateLimited(response: String): Boolean =
        response.contains("\"Information\"") || response.contains("\"Note\"")

    companion object {
        private val EQUITY_CATEGORIES = setOf("EQUITY", "COMMON STOCK")
        private val ETF_CATEGORIES = setOf("ETF", "EXCHANGE TRADED FUND")
        private val PLACEHOLDER_SYMBOLS = setOf("N/A", "NA", "-", "--")
        private const val MAX_HOLDINGS = 10
    }
}