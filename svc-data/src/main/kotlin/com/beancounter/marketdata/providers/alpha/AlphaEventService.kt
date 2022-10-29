package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class AlphaEventService(val alphaGateway: AlphaGateway, val alphaPriceAdapter: AlphaPriceAdapter) {
    @Value("\${beancounter.market.providers.ALPHA.key:demo}")
    private lateinit var apiKey: String

    fun getEvents(asset: Asset): PriceResponse {
        val json = alphaGateway.getAdjusted(asset.code, apiKey)
        if (json.contains("Error Message")) {
            log.error("Provider API error $json")
            return PriceResponse()
        }
        val priceResponse: PriceResponse = alphaPriceAdapter.alphaMapper.readValue(json, PriceResponse::class.java)
        val events = ArrayList<MarketData>()
        for (marketData in priceResponse.data) {
            if (inFilter(marketData)) {
                events.add(marketData)
            }
        }
        return PriceResponse(events)
    }

    private fun inFilter(marketData: MarketData): Boolean {
        return (marketData.isSplit() || marketData.isDividend())
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
