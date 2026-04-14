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
        topics: String? = null
    ): Map<String, Any> =
        if (topics.isNullOrBlank()) {
            restClient
                .get()
                .uri("/news?tickers={tickers}", tickers)
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(MAP_TYPE) ?: emptyMap()
        } else {
            restClient
                .get()
                .uri("/news?tickers={tickers}&topics={topics}", tickers, topics)
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(MAP_TYPE) ?: emptyMap()
        }

    companion object {
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}
    }
}