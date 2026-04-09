package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * Thin read-only client for svc-rebalance's public REST API.
 *
 * Same pattern as [RetireServiceClient] — auth forwarded via
 * [TokenService.bearerToken], return types kept generic so we aren't
 * duplicating DTO definitions from a sibling repository. Read-only methods
 * only; creating/updating/approving plans is deliberately left out so the
 * LLM can't accidentally modify investment models.
 */
@Service
class RebalanceServiceClient(
    @Qualifier("rebalanceRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    /** `GET /models` — all investment models owned by the current user. */
    fun listModels(): Map<String, Any> =
        restClient
            .get()
            .uri("/models")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve rebalance models")

    /** `GET /models/{modelId}` — a single investment model. */
    fun getModel(modelId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/models/{modelId}", modelId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve rebalance model $modelId")

    /** `GET /models/{modelId}/plans` — all plans (DRAFT + approved versions) for a model. */
    fun getPlans(modelId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/models/{modelId}/plans", modelId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve rebalance plans for model $modelId")

    /** `GET /models/{modelId}/plans/approved` — the latest approved plan for a model. */
    fun getApprovedPlan(modelId: String): Map<String, Any> =
        restClient
            .get()
            .uri("/models/{modelId}/plans/approved", modelId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve approved plan for model $modelId")

    /** `GET /models/{modelId}/plans/{planId}` — a specific plan version. */
    fun getPlan(
        modelId: String,
        planId: String
    ): Map<String, Any> =
        restClient
            .get()
            .uri("/models/{modelId}/plans/{planId}", modelId, planId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to retrieve plan $planId for model $modelId")

    companion object {
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}
    }
}