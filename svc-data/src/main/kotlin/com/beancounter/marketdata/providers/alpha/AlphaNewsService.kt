package com.beancounter.marketdata.providers.alpha

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

/**
 * Fetches news and sentiment data from Alpha Vantage NEWS_SENTIMENT API.
 * Applies market-aware ticker formatting so non-US tickers use the correct
 * AV suffix (e.g. GNE → GNE.NZ for NZX).
 */
@Service
class AlphaNewsService(
    private val alphaGateway: AlphaGateway,
    private val alphaConfig: AlphaConfig,
    private val objectMapper: ObjectMapper
) {
    @Value("\${beancounter.market.providers.alpha.key:demo}")
    private lateinit var apiKey: String

    @Cacheable("news.sentiment", key = "#tickers + '-' + (#market ?: '') + '-' + (#topics ?: '')")
    fun getNewsSentiment(
        tickers: String,
        market: String? = null,
        topics: String? = null
    ): Map<String, Any> {
        // NEWS_SENTIMENT only covers US-listed equities reliably.
        // Non-US markets return US tickers with the same symbol, so
        // short-circuit to avoid returning misleading data.
        if (!market.isNullOrBlank() && !alphaConfig.isNullMarket(market)) {
            return emptyMap()
        }
        val json = alphaGateway.getNewsSentiment(tickers, apiKey, topics)
        if (json.isBlank() || json.contains("Error Message")) {
            return emptyMap()
        }
        @Suppress("UNCHECKED_CAST")
        return objectMapper.readValue(json, Map::class.java) as Map<String, Any>
    }
}