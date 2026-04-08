package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Thin read-only client for svc-retire's public REST API.
 *
 * Follows the same pattern as jar-client's [com.beancounter.client.services.PortfolioServiceClient]
 * — inject the qualified [RestClient] and forward the current user's bearer
 * token on every call so downstream `@PreAuthorize` checks pass.
 *
 * Return types are deliberately `Map<String, Any>` / `List<*>` rather than
 * typed DTOs: svc-retire's models live in a separate repo, and the LLM
 * consumes the JSON directly via Spring AI tool-call serialization, so
 * parallel DTO maintenance buys nothing. Read-only endpoints only — we don't
 * want the LLM mutating retirement plans without explicit user intent.
 */
@Service
class RetireServiceClient(
    @Qualifier("retireRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    /** `GET /plans` — all retirement plans owned by the current user. */
    fun listPlans(): Map<String, Any> =
        restClient
            .get()
            .uri("/plans")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve retirement plans")

    /** `GET /plans/{planId}` — a single retirement plan by id. */
    fun getPlan(planId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/plans/{planId}", planId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve retirement plan $planId")

    /** `GET /plans/{planId}/all-expenses` — plan with working + retirement expenses split. */
    fun getPlanWithExpenses(planId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/plans/{planId}/all-expenses", planId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve expenses for plan $planId")

    /** `GET /plans/{planId}/contributions` — pension/insurance contributions for a plan. */
    fun getContributions(planId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/plans/{planId}/contributions", planId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve contributions for plan $planId")

    /**
     * `GET /projection/plans/{planId}/financials` — current financial
     * position (liquid vs non-spendable assets, FI number, FI progress).
     * Optionally converts to a display currency.
     */
    fun getFinancials(
        planId: String,
        displayCurrency: String? = null
    ): Map<String, Any> {
        val spec =
            restClient
                .get()
                .uri { builder ->
                    builder
                        .path("/projection/plans/{planId}/financials")
                        .apply {
                            if (!displayCurrency.isNullOrBlank()) queryParam("displayCurrency", displayCurrency)
                        }.build(planId)
                }.header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
        return spec
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve financials for plan $planId")
    }

    companion object {
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}
    }
}
