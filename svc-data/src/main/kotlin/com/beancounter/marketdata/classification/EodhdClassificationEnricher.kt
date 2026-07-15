package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset
import com.beancounter.common.model.AssetClassification
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
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
    private val classificationService: ClassificationService
) : ClassificationEnricher {
    private val log = LoggerFactory.getLogger(EodhdClassificationEnricher::class.java)

    override fun canEnrich(asset: Asset): Boolean = ClassificationEnricher.categoryCanEnrich(asset)

    override fun isEtf(asset: Asset): Boolean = ClassificationEnricher.categoryIsEtf(asset)

    override fun isEquity(asset: Asset): Boolean = ClassificationEnricher.categoryIsEquity(asset)

    override fun enrichClassification(asset: Asset): Boolean =
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
            log.warn("Failed to enrich classification for ${asset.code}: ${e.message}")
            false
        }

    private fun enrichEquity(asset: Asset): Boolean {
        val symbol = eodhdConfig.getPriceCode(asset)
        val general = eodhdProxy.getFundamentals(symbol, eodhdConfig.apiKey).general

        if (general?.sector.isNullOrBlank()) {
            log.debug("No sector in fundamentals for $symbol")
            return false
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
        return true
    }

    private fun enrichEtf(asset: Asset): Boolean {
        val symbol = eodhdConfig.getPriceCode(asset)
        val sectorWeights = eodhdProxy.getFundamentals(symbol, eodhdConfig.apiKey).etfData?.sectorWeights

        if (sectorWeights.isNullOrEmpty()) {
            log.debug("No sector weights in fundamentals for $symbol")
            return false
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
                weight = weight
            )
            sectorCount++
        }

        log.info("Added $sectorCount sector exposures for ${asset.code}")
        return sectorCount > 0
    }
}