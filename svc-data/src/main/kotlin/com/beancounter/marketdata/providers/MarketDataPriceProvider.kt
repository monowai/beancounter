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

    /**
     * Determine the effective date to use when retrieving prices for the given market and price request.
     *
     * @param market The market for which the date is being resolved.
     * @param priceRequest The price request whose parameters influence the chosen date.
     * @return The LocalDate that should be used to fetch market data for this request.
     */
    fun getDate(
        market: Market,
        priceRequest: PriceRequest
    ): LocalDate

    // fromDate caps how far back to fetch when the provider supports a date
    // range. Providers that always return their full available history
    /**
     * Retrieves historical price data for the given asset starting from a cutoff date.
     *
     * Implementations should return available historical prices for `asset` beginning at or after `fromDate`. Providers may return full available history, a limited range starting at `fromDate`, or no historical data at all; some providers (e.g., cash, custom, or assets marked as alpha/none) may ignore `fromDate`.
     *
     * @param asset The asset to retrieve historical prices for.
     * @param fromDate The earliest date (inclusive) for returned prices; defaults to two years before the current date.
     * @return A PriceResponse containing the historical prices available for `asset`, subject to the provider's available history and any applied caps.
     */
    fun backFill(
        asset: Asset,
        fromDate: LocalDate = LocalDate.now().minusYears(2)
    ): PriceResponse

    /**
 * Indicates whether the provider requires an external API call to fulfill requests.
 *
 * @return `true` if the provider requires an external API call, `false` otherwise.
 */
    fun isApiSupported(): Boolean
}