package com.beancounter.marketdata.broker.reconcile

import com.beancounter.common.model.MarketData
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.providers.ProviderUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

/**
 * An asset's closing prices from before the stored window, asked of its market-data
 * provider and kept nowhere: they are only for suggesting when a long-held holding was
 * opened. Only a provider whose closes are split-adjusted is asked, so the prices are on
 * the same basis as a statement's cost per share.
 */
@Service
class ProviderHistory(
    private val providerUtils: ProviderUtils,
    private val assetFinder: AssetFinder,
    private val dateUtils: DateUtils
) {
    /**
     * The adjusted closes dated before [before], as far back as the provider goes; none for
     * an unknown asset.
     */
    fun closesBefore(
        assetId: String,
        before: LocalDate
    ): List<Pair<LocalDate, BigDecimal>> {
        val asset = assetFinder.findOrNull(assetId) ?: return emptyList()
        val from = dateUtils.date.minusYears(MAX_HISTORY_YEARS)
        return providerUtils
            .splitProviders(providerUtils.getInputs(listOf(asset)))
            .keys
            .filter { it.shipsAdjustedClose() }
            .flatMap { it.backFill(asset, from).data }
            .filter { it.priceDate.isBefore(before) && it.close.signum() > 0 }
            .map { it.priceDate to it.close }
    }

    /**
     * The provider's splits dated on or after [from] and before [before], whatever its closes:
     * a split's ratio does not depend on price adjustment. None for an unknown asset.
     */
    fun splitsBetween(
        assetId: String,
        from: LocalDate,
        before: LocalDate
    ): List<Pair<LocalDate, BigDecimal>> {
        val asset = assetFinder.findOrNull(assetId) ?: return emptyList()
        return providerUtils
            .splitProviders(providerUtils.getInputs(listOf(asset)))
            .keys
            .flatMap { it.backFill(asset, from).data }
            .filter { MarketData.isSplit(it) && !it.priceDate.isBefore(from) && it.priceDate.isBefore(before) }
            .map { it.priceDate to it.split }
            .distinctBy { it.first }
    }

    companion object {
        /** How far back to ask: further than any holding a statement shows is likely held. */
        const val MAX_HISTORY_YEARS = 40L
    }
}