package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.AssetSearchResult

/**
 * The search surface of a price provider that has one (EODHD, FX pairs). Price providers without
 * a search endpoint do not implement it. A market-scoped search asks the provider that prices the
 * market, so the search surface and the price surface agree; an unscoped search asks them all.
 */
interface AssetSearchProvider {
    /** Id of the [MarketDataPriceProvider] whose markets this search serves. */
    fun getId(): String

    /**
     * Search the provider for assets matching `keyword`.
     *
     * `market` is the BC market code the caller is scoping to, or `null` for an unscoped search
     * (e.g. the global header bar).
     */
    fun searchAssets(
        keyword: String,
        market: String?
    ): List<AssetSearchResult>
}