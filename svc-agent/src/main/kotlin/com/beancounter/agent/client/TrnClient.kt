package com.beancounter.agent.client

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.TrnResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Thin client for svc-data's transaction API — only what the agent's
 * holding tool needs: the buys and sells of one asset in one portfolio.
 */
@Service
class TrnClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun getTrades(
        portfolioId: String,
        assetId: String
    ): TrnResponse =
        restClient
            .get()
            .uri("/trns/{portfolioId}/asset/{assetId}/trades", portfolioId, assetId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body<TrnResponse>()
            ?: TrnResponse()
}