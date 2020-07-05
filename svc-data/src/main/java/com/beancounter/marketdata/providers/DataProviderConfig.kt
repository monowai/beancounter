package com.beancounter.marketdata.providers

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import java.time.LocalDate

interface DataProviderConfig {
    /**
     * MarketDataProviders have difference API restrictions. Number of assets in a single call is
     * one of them.
     *
     * @return Number of Assets to request in a single call.
     */
    fun getBatchSize(): Int
    fun getMarketDate(market: Market, date: String): LocalDate?
    fun getPriceCode(asset: Asset): String
}