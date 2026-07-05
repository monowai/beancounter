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
     * Null-safe, delimiter-aware market-code membership check shared by all price providers.
     *
     * A null or blank [markets] allowlist means the provider supports no markets.
     * The allowlist is comma-separated; each token is trimmed before comparison so
     * `"ASX, NZX"` and `"ASX,NZX"` are equivalent.  Matching is case-sensitive and
     * exact — `"SX"` does **not** match an entry `"ASX"`.
     */
    fun supportsMarketCode(
        markets: String?,
        code: String
    ): Boolean = !markets.isNullOrBlank() && markets.split(",").any { it.trim() == code }
}