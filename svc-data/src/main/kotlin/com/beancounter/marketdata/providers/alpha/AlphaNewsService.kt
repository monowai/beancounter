package com.beancounter.marketdata.providers.alpha

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/**
 * Fetches news and sentiment data from Alpha Vantage NEWS_SENTIMENT API.
 */
@Service
class AlphaNewsService(
    private val alphaGateway: AlphaGateway,
    private val objectMapper: ObjectMapper
) {
    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    fun getNewsSentiment(
        tickers: String,
        topics: String? = null
    ): Map<String, Any> {
        val json = alphaGateway.getNewsSentiment(tickers, apiKey, topics)
        if (json.isBlank() || json.contains("Error Message")) {
            return emptyMap()
        }
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(json, Map::class.java) as Map<String, Any>
    }
}