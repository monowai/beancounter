package com.beancounter.client

import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.model.Market

interface MarketService {
    fun getMarkets(): MarketResponse
    fun getMarket(marketCode: String): Market
}
