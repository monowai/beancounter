package com.beancounter.marketdata.providers

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