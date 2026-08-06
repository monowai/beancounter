package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdPriceService
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Enriches assets with classification data from EODHD's Fundamentals endpoint.
 *
 * - Equities: `General.Sector` / `General.Industry`
 * - ETFs: `ETF_Data.Sector_Weights` (weighted, multi-sector)
 *
 * Parallel to [AlphaClassificationEnricher]. EODHD returns Morningstar-style sector names which
 * [SectorNormalizer] maps onto the canonical GICS names shared across providers. Stored under the
 * dedicated EODHD [com.beancounter.common.model.ClassificationStandard], so EODHD and AlphaVantage
 * exposures coexist per asset without clobbering each other.
 */
@Service
class EodhdClassificationEnricher(
    private val eodhdConfig: EodhdConfig,
    private val eodhdProxy: EodhdProxy,
    private val classificationService: ClassificationService,
    private val dateUtils: DateUtils = DateUtils()
) : ClassificationEnricher {
    private val log = LoggerFactory.getLogger(EodhdClassificationEnricher::class.java)

    override fun canEnrich(asset: Asset): Boolean = ClassificationEnricher.categoryCanEnrich(asset)

    override fun isEtf(asset: Asset): Boolean = ClassificationEnricher.categoryIsEtf(asset)

    override fun isEquity(asset: Asset): Boolean = ClassificationEnricher.categoryIsEquity(asset)

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
            // EODHD signals quota exhaustion via HTTP status (thrown as an exception by the
            // REST client), not a 200-body throttle marker like AlphaVantage - map straight to
            // FAILED rather than inventing EODHD-specific rate-limit parsing.
            log.warn("Failed to enrich classification for ${asset.code}: ${e.message}")
            EnrichmentResult.FAILED
        }

    /**
     * See [AlphaClassificationEnricher.logNoData] — a NO_DATA outcome stamps the asset as checked
     * and silences it for the staleness window, so it has to be visible at WARN with the exact
     * symbol that was queried.
     */
    private fun logNoData(
        asset: Asset,
        symbol: String,
        reason: String
    ) = log.warn(
        "No classification data from {} for symbol {} ({}:{}) - {}",
        EodhdPriceService.ID,
        symbol,
        asset.market.code,
        asset.code,
        reason
    )

    private fun enrichEquity(asset: Asset): EnrichmentResult {
        val symbol = eodhdConfig.getPriceCode(asset)
        val general = eodhdProxy.getFundamentals(symbol, eodhdConfig.apiKey).general

        if (general?.sector.isNullOrBlank()) {
            logNoData(asset, symbol, "fundamentals carried no General.Sector")
            return EnrichmentResult.NO_DATA
        }

        val standard = classificationService.getEodhdStandard()

        val sectorItem =
            classificationService.getOrCreateItem(
                standard = standard,
                level = ClassificationLevel.SECTOR,
                rawCode = general.sector
            )

        classificationService.classifyAsset(
            asset = asset,
            standard = standard,
            item = sectorItem,
            level = ClassificationLevel.SECTOR,
            source = AssetClassification.SOURCE_EODHD
        )

        if (!general.industry.isNullOrBlank()) {
            val industryItem =
                classificationService.getOrCreateItem(
                    standard = standard,
                    level = ClassificationLevel.INDUSTRY,
                    name = general.industry,
                    parent = sectorItem
                )

            classificationService.classifyAsset(
                asset = asset,
                standard = standard,
                item = industryItem,
                level = ClassificationLevel.INDUSTRY,
                source = AssetClassification.SOURCE_EODHD
            )
        }

        log.info("Classified ${asset.code} as ${general.sector} / ${general.industry ?: "N/A"}")
        return EnrichmentResult.ENRICHED
    }

    private fun enrichEtf(asset: Asset): EnrichmentResult {
        val symbol = eodhdConfig.getPriceCode(asset)
        val sectorWeights = eodhdProxy.getFundamentals(symbol, eodhdConfig.apiKey).etfData?.sectorWeights

        if (sectorWeights.isNullOrEmpty()) {
            logNoData(asset, symbol, "fundamentals carried no ETF_Data.Sector_Weights")
            return EnrichmentResult.NO_DATA
        }

        val standard = classificationService.getEodhdStandard()
        classificationService.clearExposures(asset.id)

        var sectorCount = 0
        for ((sectorName, weightData) in sectorWeights) {
            val weight = weightData.equityPct?.toBigDecimalOrNull()
            if (weight == null || weight.signum() <= 0) {
                continue
            }

            val sectorItem =
                classificationService.getOrCreateItem(
                    standard = standard,
                    level = ClassificationLevel.SECTOR,
                    rawCode = sectorName
                )

            classificationService.addExposure(
                asset = asset,
                standard = standard,
                item = sectorItem,
                weight = weight,
                asOf = dateUtils.date
            )
            sectorCount++
        }

        log.info("Added $sectorCount sector exposures for ${asset.code}")
        return if (sectorCount > 0) EnrichmentResult.ENRICHED else EnrichmentResult.NO_DATA
    }
}