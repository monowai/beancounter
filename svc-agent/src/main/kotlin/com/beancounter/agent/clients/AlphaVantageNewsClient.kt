package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
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
            .body(JSON_MAP) ?: emptyMap()
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
            .body(JSON_MAP) ?: emptyMap()
    }

    /**
     * Broad macro/topic news via svc-data's `/news/topic` endpoint. [topics] are provider topic
     * tags (e.g. "stock markets", "economy") the caller has already chosen — comma-joined, same
     * shape as [getMarketNews].
     */
    fun getTopicNews(topics: List<String>): Map<String, Any> {
        val params = mapOf("topics" to topics.joinToString(","))
        return restClient
            .get()
            .uri("/news/topic?topics={topics}", params)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(JSON_MAP) ?: emptyMap()
    }

    /**
     * Daily Positive / Neutral / Negative article counts per macro topic and per sector, via
     * svc-data's `/news/sentiment/market` endpoint. A stored-row read; no news provider is called.
     * svc-data always returns a `data` list (empty when nothing is stored), so a missing body is a
     * contract failure and throws [BusinessException] rather than passing for "no coverage".
     */
    fun getMarketSentiment(days: Int): Map<String, Any> =
        restClient
            .get()
            .uri("/news/sentiment/market?days={days}", days)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(JSON_MAP) ?: throw BusinessException("Failed to retrieve market news sentiment")

    /**
     * Daily aggregated news sentiment per asset id, via svc-data's `/news/sentiment` endpoint.
     * Assets with no coverage are absent from the returned `data` map; a missing body is a
     * contract failure and throws [BusinessException] rather than passing for "no coverage".
     */
    fun getAssetSentiment(
        assetIds: List<String>,
        days: Int
    ): Map<String, Any> =
        restClient
            .get()
            .uri("/news/sentiment?assetIds={assetIds}&days={days}", assetIds.joinToString(","), days)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(JSON_MAP) ?: throw BusinessException("Failed to retrieve asset news sentiment")
}