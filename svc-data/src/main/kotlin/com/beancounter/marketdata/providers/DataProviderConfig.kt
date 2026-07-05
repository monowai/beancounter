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

    /**
     * Null-safe market-code membership check shared by all price providers.
     *
     * A null or blank [markets] allowlist means the provider supports no markets.
     * Preserves the MarketStack `isBlank()` guard alongside Alpha's `?.contains` pattern:
     * both collapse to `false` here without a `!!` or NPE risk.
     */
    fun supportsMarketCode(
        markets: String?,
        code: String
    ): Boolean = !markets.isNullOrBlank() && markets.contains(code)
}