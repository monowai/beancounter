package com.beancounter.marketdata.classification.enricher

import com.beancounter.common.model.Asset
import com.beancounter.common.model.ClassificationLevel
import com.beancounter.common.model.ClassificationStandard
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.classification.ClassificationTaxonomy
import com.beancounter.marketdata.classification.FundLookThrough
import com.beancounter.marketdata.providers.alpha.AlphaEtfHolding
import com.beancounter.marketdata.providers.alpha.AlphaEtfSector
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Writes an AlphaVantage ETF_PROFILE into the fund look-through: sector exposures against the
 * ALPHA standard, and the fund's top holdings. Each write replaces what the asset held before.
 */
@Component
class AlphaEtfLookThrough(
    private val taxonomy: ClassificationTaxonomy,
    private val fundLookThrough: FundLookThrough,
    private val dateUtils: DateUtils = DateUtils()
) {
    /**
     * Replaces the asset's sector exposures with the positively weighted [sectors].
     * Returns the number added.
     */
    fun replaceSectorExposures(
        asset: Asset,
        sectors: List<AlphaEtfSector>
    ): Int {
        val standard = taxonomy.providerStandard(ClassificationStandard.ALPHA)
        fundLookThrough.clearExposures(asset.id)
        var sectorCount = 0
        for (sectorData in sectors) {
            val weight = sectorData.weight.toBigDecimalOrNull()
            if (weight == null || weight.signum() <= 0) {
                continue
            }
            val sectorItem =
                taxonomy.getOrCreateItem(
                    standard = standard,
                    level = ClassificationLevel.SECTOR,
                    rawCode = sectorData.sector
                )
            fundLookThrough.addExposure(
                asset = asset,
                standard = standard,
                item = sectorItem,
                weight = weight,
                asOf = dateUtils.date
            )
            sectorCount++
        }
        return sectorCount
    }

    /**
     * Replaces the asset's top holdings, up to [MAX_HOLDINGS]. Returns the number added.
     *
     * Skips placeholder symbols (AlphaVantage returns literal "n/a" for unlisted positions) and
     * de-duplicates within the batch, since two identical symbols for the same parent asset
     * violate the asset_holding unique constraint and roll back the whole enrichment (including
     * the sector exposures).
     */
    fun replaceTopHoldings(
        asset: Asset,
        holdings: List<AlphaEtfHolding>
    ): Int {
        fundLookThrough.clearHoldings(asset.id)
        val accepted =
            holdings
                .asSequence()
                .mapNotNull { acceptedHolding(it) }
                .distinctBy { it.symbol }
                .take(MAX_HOLDINGS)
                .toList()
        // Persist the normalized form: the unique constraint is case-sensitive, so storing raw
        // casing would let the same ticker land twice across runs.
        accepted.forEach {
            fundLookThrough.addHolding(
                asset = asset,
                symbol = it.symbol,
                name = it.description,
                weight = it.weight,
                asOf = dateUtils.date
            )
        }
        return accepted.size
    }

    private fun acceptedHolding(holding: AlphaEtfHolding): AcceptedHolding? {
        val weight = holding.weight?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        val symbol =
            holding.symbol
                ?.trim()
                ?.uppercase()
                ?.takeUnless { it.isBlank() || it in PLACEHOLDER_SYMBOLS }
        return if (weight == null || symbol == null) null else AcceptedHolding(symbol, holding.description, weight)
    }

    /** A holding row that is fit to persist: a real, normalized symbol with a positive weight. */
    private data class AcceptedHolding(
        val symbol: String,
        val description: String?,
        val weight: BigDecimal
    )

    companion object {
        private val PLACEHOLDER_SYMBOLS = setOf("N/A", "NA", "-", "--")
        const val MAX_HOLDINGS = 10
    }
}