package com.beancounter.marketdata.providers.alpha

import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.MarketData.Companion.isDividend
import com.beancounter.common.model.MarketData.Companion.isSplit
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Obtains corporate actions since inception. Alpha stores these in price history.  This
 * service will drop anything that is not considered an event.
 */
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
        return (isSplit(marketData) || isDividend(marketData))
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
