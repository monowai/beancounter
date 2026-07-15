package com.beancounter.marketdata.classification

import com.beancounter.common.model.Asset

/**
 * Enriches an asset with sector/industry classification and (for ETFs) weighted sector exposures
 * from a market-data provider.
 *
 * Implementations are provider-specific (AlphaVantage, EODHD). The active provider is selected by
 * [ClassificationEnricherConfig] and injected wherever enrichment is consumed.
 */
interface ClassificationEnricher {
    /** True when this asset is an equity or ETF that can carry classification data. */
    fun canEnrich(asset: Asset): Boolean

    /** True when this asset is an ETF (exposure-weighted, multi-sector). */
    fun isEtf(asset: Asset): Boolean

    /** True when this asset is an equity (single sector/industry). */
    fun isEquity(asset: Asset): Boolean

    /** Fetch and persist classification data. Returns true when anything was stored. */
    fun enrichClassification(asset: Asset): Boolean

    companion object {
        val EQUITY_CATEGORIES = setOf("EQUITY", "COMMON STOCK")
        val ETF_CATEGORIES = setOf("ETF", "EXCHANGE TRADED FUND")

        fun categoryCanEnrich(asset: Asset): Boolean {
            val category = asset.category.uppercase()
            return category in EQUITY_CATEGORIES || category in ETF_CATEGORIES
        }

        fun categoryIsEtf(asset: Asset): Boolean = asset.category.uppercase() in ETF_CATEGORIES

        fun categoryIsEquity(asset: Asset): Boolean = asset.category.uppercase() in EQUITY_CATEGORIES
    }
}