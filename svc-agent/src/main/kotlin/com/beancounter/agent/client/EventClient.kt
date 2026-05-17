package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Thin client for the svc-event REST API. Responses are returned as
 * `Map<String, Any>` because the strongly-typed response contracts live in the
 * svc-event module, which svc-agent does not depend on. The agent only needs to
 * relay these to the LLM as JSON, so a map is sufficient.
 */
@Service
class EventClient(
    @Qualifier("bcEventRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    private val mapType = object : ParameterizedTypeReference<Map<String, Any>>() {}

    fun getAssetEvents(assetId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/asset/{assetId}", assetId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(mapType)
            ?: emptyMap()

    fun loadPortfolioEvents(
        portfolioId: String,
        asAt: String
    ): Map<String, Any> =
        restClient
            .post()
            .uri("/load/{portfolioId}?asAt={asAt}", portfolioId, asAt)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(mapType)
            ?: emptyMap()

    fun backfillPortfolio(
        portfolioId: String,
        fromDate: String,
        toDate: String
    ): Map<String, Any> =
        restClient
            .post()
            .uri("/backfill/{portfolioId}?fromDate={fromDate}&toDate={toDate}", portfolioId, fromDate, toDate)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(mapType)
            ?: emptyMap()
}