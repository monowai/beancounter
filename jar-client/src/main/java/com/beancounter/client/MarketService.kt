package com.beancounter.client

import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.model.Market

/**
 * Obtain all markets or a selected market.
 */
interface MarketService {
    fun getMarkets(): MarketResponse
    fun getMarket(marketCode: String): Market
}
