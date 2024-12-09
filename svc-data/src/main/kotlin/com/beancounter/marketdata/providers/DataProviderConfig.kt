package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import java.time.LocalDate

/**
 * MarketDataProviders have difference API restrictions. This interface identifies common restrictions.
 */
interface DataProviderConfig {
    /**
     * @return Number of Assets to request in a single call.
     */
    fun getBatchSize(): Int

    fun getMarketDate(
        market: Market,
        date: String,
        currentMode: Boolean = true
    ): LocalDate

    fun getPriceCode(asset: Asset): String
}