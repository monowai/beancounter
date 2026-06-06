package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Client for news and sentiment data via svc-data's news endpoint.
 * svc-data handles the Alpha Vantage integration internally.
 */
@Service
class AlphaVantageNewsClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun getNewsSentiment(
        tickers: String,
        market: String? = null,
        topics: String? = null
    ): Map<String, Any> {
        val params = mutableMapOf<String, String>("tickers" to tickers)
        val queryParts = mutableListOf("tickers={tickers}")
        if (!market.isNullOrBlank()) {
            params["market"] = market
            queryParts.add("market={market}")
        }
        if (!topics.isNullOrBlank()) {
            params["topics"] = topics
            queryParts.add("topics={topics}")
        }
        val uri = "/news?" + queryParts.joinToString("&")
        return restClient
            .get()
            .uri(uri, params)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE) ?: emptyMap()
    }

    /**
     * Market / sector news via svc-data's `/news/market` endpoint. [symbols] are verbatim EODHD
     * proxy symbols (an index or a sector ETF) the caller has already chosen.
     */
    fun getMarketNews(
        symbols: List<String>,
        topics: String? = null
    ): Map<String, Any> {
        val params = mutableMapOf("symbols" to symbols.joinToString(","))
        val queryParts = mutableListOf("symbols={symbols}")
        if (!topics.isNullOrBlank()) {
            params["topics"] = topics
            queryParts.add("topics={topics}")
        }
        val uri = "/news/market?" + queryParts.joinToString("&")
        return restClient
            .get()
            .uri(uri, params)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE) ?: emptyMap()
    }

    companion object {
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}
    }
}