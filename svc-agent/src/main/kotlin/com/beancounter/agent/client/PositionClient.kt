package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.PositionResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Thin client for the svc-position REST API. Uses the user's bearer token from
 * the current SecurityContext (via [TokenService]) so downstream authorization
 * runs against the calling user, not a service account.
 */
@Service
class PositionClient(
    @Qualifier("bcPositionRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun getPositionsByCode(
        portfolioCode: String,
        asAt: String = "today",
        includeValues: Boolean = true
    ): PositionResponse =
        restClient
            .get()
            .uri("/{code}?asAt={asAt}&value={value}", portfolioCode, asAt, includeValues)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body<PositionResponse>()
            ?: PositionResponse()

    /**
     * Lookup positions by portfolio id. Required for managed (shared)
     * portfolios because portfolio code is unique only within an owner —
     * the by-code path 404s for an adviser viewing a client's portfolio.
     */
    fun getPositionsById(
        portfolioId: String,
        asAt: String = "today",
        includeValues: Boolean = true
    ): PositionResponse =
        restClient
            .get()
            .uri("/id/{id}?asAt={asAt}&value={value}", portfolioId, asAt, includeValues)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body<PositionResponse>()
            ?: PositionResponse()
}