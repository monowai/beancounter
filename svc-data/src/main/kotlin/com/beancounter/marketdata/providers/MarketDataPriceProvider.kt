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
        priceRequest: PriceRequest,
    ): LocalDate

    fun backFill(asset: Asset): PriceResponse

    // Return true if an external API calls is required
    fun isApiSupported(): Boolean
}
