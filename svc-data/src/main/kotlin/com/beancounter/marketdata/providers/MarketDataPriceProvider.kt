package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.AssetSearchResult
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import java.time.LocalDate

/**
 * Standard interface to retrieve MarketData information from an implementor.
 *
 * @author mikeh
 * @since 2019-01-27
 */
interface MarketDataPriceProvider {
    fun getMarketData(priceRequest: PriceRequest): Collection<MarketData>

    /**
     * Convenience function to return the ID.
     *
     * @return Unique id of the MarketDataProvider
     */
    fun getId(): String

    fun isMarketSupported(market: Market): Boolean

    fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate

    // fromDate caps how far back to fetch when the provider supports a date
    // range. Providers that always return their full available history
    // (Alpha) or none at all (cash, custom) may ignore it.
    fun backFill(
        asset: Asset,
        fromDate: LocalDate = defaultBackfillFrom()
    ): PriceResponse

    // Return true if an external API calls is required
    fun isApiSupported(): Boolean

    /**
     * `true` when this provider persists rows whose `close` is already
     * split- and dividend-adjusted (e.g. EODHD via `adjusted_close`,
     * Alpha `TIME_SERIES_DAILY_ADJUSTED`). Drives [SplitAdjuster] to
     * skip dividing rows from this source so the read path doesn't
     * double-adjust on top of provider-side adjustment.
     *
     * Default is `false`: most legacy providers (Alpha `TIME_SERIES_DAILY`,
     * MarketStack raw close, Morningstar, Cash, Private) ship raw closes
     * and need read-time rebasement.
     */
    fun shipsAdjustedClose(): Boolean = false

    /**
     * Search the provider for assets matching `keyword`. Providers that have no search surface
     * (cash, custom, morningstar) return the empty default; price providers wired to a real
     * search endpoint (EODHD, AlphaVantage, MarketStack) override.
     *
     * `market` is the BC market code the caller is scoping to, or `null` for an unscoped search
     * (e.g. the global header bar). Providers that require a market — like MarketStack, which
     * needs an exchange MIC — return empty for `null` rather than fabricating a default.
     */
    fun searchAssets(
        keyword: String,
        market: String?
    ): List<AssetSearchResult> = emptyList()

    companion object {
        // Default window when no caller-supplied `fromDate`. Matches the
        // shortest provider plan (EODHD historical / MarketStack base tier
        // both cover roughly this range). PriceController + ensureHistory
        // pass an explicit `targetFrom` for deep-history charts.
        const val DEFAULT_BACKFILL_YEARS = 2L

        // Hard floor for any backfill request. Matches the longest range
        // the chart UI exposes and caps how far back providers will be
        // asked to reach. MarketDataBackfillService enforces this floor
        // after anchoring to earliest cross-portfolio tradeDate.
        const val MAX_BACKFILL_YEARS = 10L

        fun defaultBackfillFrom(): LocalDate = LocalDate.now().minusYears(DEFAULT_BACKFILL_YEARS)
    }
}