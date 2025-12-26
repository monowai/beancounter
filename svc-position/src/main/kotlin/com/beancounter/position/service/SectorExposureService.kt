package com.beancounter.position.service

import com.beancounter.client.services.MarketDataClient
import com.beancounter.common.contracts.SectorExposure
import com.beancounter.common.contracts.SectorExposureData
import com.beancounter.common.model.AssetCategory
import com.beancounter.common.model.ClassificationItem
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Service for calculating sector exposure from portfolio positions.
 *
 * - ETFs: Uses weighted sector allocations from stored exposures
 * - Equities: Uses 100% allocation to classified sector
 * - Other: Groups by report category as "Unclassified"
 */
@Service
class SectorExposureService(
    private val marketDataClient: MarketDataClient
) {
    private val log = LoggerFactory.getLogger(SectorExposureService::class.java)

    /**
     * Calculate sector exposure from positions.
     *
     * @param positions The valued positions to analyze
     * @param valueIn Which currency context to use (BASE, PORTFOLIO, or TRADE)
     * @return SectorExposureData with sector breakdown
     */
    fun calculateSectorExposure(
        positions: Positions,
        valueIn: Position.In = Position.In.PORTFOLIO
    ): SectorExposureData {
        if (!positions.hasPositions()) {
            return SectorExposureData()
        }

        val totals = positions.totals[valueIn]
        val totalValue = totals?.marketValue ?: BigDecimal.ZERO
        val currency = totals?.currency?.code ?: positions.portfolio.currency.code

        if (totalValue == BigDecimal.ZERO) {
            return SectorExposureData(currency = currency)
        }

        // Accumulate market value by sector
        val sectorValues = mutableMapOf<String, BigDecimal>()

        for (position in positions.positions.values) {
            val marketValue = position.moneyValues[valueIn]?.marketValue ?: BigDecimal.ZERO
            if (marketValue <= BigDecimal.ZERO) continue

            val asset = position.asset
            when (val reportCategory = asset.effectiveReportCategory) {
                AssetCategory.REPORT_ETF -> {
                    // ETF: Distribute market value by sector weights
                    distributeEtfExposure(asset.id, marketValue, sectorValues)
                }
                AssetCategory.REPORT_EQUITY -> {
                    // Equity: 100% to its classified sector
                    distributeEquityClassification(asset.id, marketValue, sectorValues)
                }
                else -> {
                    // Other: Group as the report category
                    val sector = reportCategory
                    sectorValues[sector] = (sectorValues[sector] ?: BigDecimal.ZERO) + marketValue
                }
            }
        }

        // Convert to exposure list with percentages
        val exposures =
            sectorValues
                .map { (sector, value) ->
                    val percentage =
                        value
                            .divide(totalValue, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal(100))
                            .setScale(2, RoundingMode.HALF_UP)
                    SectorExposure(
                        sector = sector,
                        marketValue = value.setScale(2, RoundingMode.HALF_UP),
                        percentage = percentage
                    )
                }.sortedByDescending { it.percentage }

        return SectorExposureData(
            exposures = exposures,
            totalValue = totalValue.setScale(2, RoundingMode.HALF_UP),
            currency = currency
        )
    }

    private fun distributeEtfExposure(
        assetId: String,
        marketValue: BigDecimal,
        sectorValues: MutableMap<String, BigDecimal>
    ) {
        try {
            val exposures = marketDataClient.getExposures(assetId)
            if (exposures.isEmpty()) {
                // No exposures found, classify as Unclassified
                sectorValues[ClassificationItem.UNCLASSIFIED] =
                    (sectorValues[ClassificationItem.UNCLASSIFIED] ?: BigDecimal.ZERO) + marketValue
                return
            }

            for (exposure in exposures) {
                val sectorName = exposure.item.name
                val weightedValue =
                    marketValue.multiply(exposure.weight).divide(BigDecimal(100), 4, RoundingMode.HALF_UP)
                sectorValues[sectorName] = (sectorValues[sectorName] ?: BigDecimal.ZERO) + weightedValue
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch exposures for ETF $assetId: ${e.message}")
            sectorValues[ClassificationItem.UNCLASSIFIED] =
                (sectorValues[ClassificationItem.UNCLASSIFIED] ?: BigDecimal.ZERO) + marketValue
        }
    }

    private fun distributeEquityClassification(
        assetId: String,
        marketValue: BigDecimal,
        sectorValues: MutableMap<String, BigDecimal>
    ) {
        try {
            val classifications = marketDataClient.getClassifications(assetId)
            // Find sector-level classification
            val sectorClassification = classifications.find { it.level == ClassificationLevel.SECTOR }

            if (sectorClassification != null) {
                val sectorName = sectorClassification.item.name
                sectorValues[sectorName] = (sectorValues[sectorName] ?: BigDecimal.ZERO) + marketValue
            } else {
                // No sector classification, mark as Unclassified
                sectorValues[ClassificationItem.UNCLASSIFIED] =
                    (sectorValues[ClassificationItem.UNCLASSIFIED] ?: BigDecimal.ZERO) + marketValue
            }
        } catch (e: Exception) {
            log.warn("Failed to fetch classifications for Equity $assetId: ${e.message}")
            sectorValues[ClassificationItem.UNCLASSIFIED] =
                (sectorValues[ClassificationItem.UNCLASSIFIED] ?: BigDecimal.ZERO) + marketValue
        }
    }
}