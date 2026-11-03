package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import com.fasterxml.jackson.annotation.JsonInclude
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient

/**
 * A single phase in a composite (multi-plan) retirement projection.
 *
 * The LLM populates a list of these when the user asks about multi-stage
 * retirements ("plan A from 35 to 50, then plan B from 50 onward"). Kept
 * as a simple data class rather than importing svc-retire's CompositePhase
 * directly so svc-agent has no compile-time dependency on the sibling repo.
 *
 * - [planId]    Phase id (a Plan's `phases` from listIndependencePlans, or listRetirementPlans)
 * - [fromAge]   Age at which this phase becomes active (inclusive)
 * - [toAge]     Age at which this phase ends; null means "until death / last phase"
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class CompositePhaseInput(
    val planId: String,
    val fromAge: Int,
    val toAge: Int? = null
)

/**
 * svc-retire's compute endpoints, for one phase or a composite of phases.
 * The current user's bearer token is forwarded on every call so downstream
 * `@PreAuthorize` checks pass; results are the JSON maps the LLM consumes
 * directly.
 *
 * These POST endpoints are compute-only — they run projections and
 * simulations against a plan's parameters but never persist anything.
 * Safe to expose to the agent even though they're POST (the method is
 * POST because the request bodies can be large, not because they mutate).
 */
@Service
class RetireProjectionClient(
    @Qualifier("retireRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    /**
     * `POST /projection/plans/{planId}` — deterministic year-by-year projection
     * for a single plan. Returns runway, depletion age, yearly balances.
     */
    fun runProjection(
        planId: String,
        displayCurrency: String? = null
    ): Map<String, Any> =
        post(
            withCurrency(mutableMapOf(), displayCurrency),
            "Failed to run projection for plan $planId",
            "/projection/plans/{planId}",
            planId
        )

    /**
     * `POST /projection/plans/{planId}/scenarios` — deterministic comparison
     * across Base / Conservative / Optimistic / Liquid-Only scenarios.
     */
    fun runScenarios(
        planId: String,
        displayCurrency: String? = null
    ): Map<String, Any> =
        post(
            withCurrency(mutableMapOf(), displayCurrency),
            "Failed to run scenarios for plan $planId",
            "/projection/plans/{planId}/scenarios",
            planId
        )

    /**
     * `POST /projection/plans/{planId}/monte-carlo` — Monte Carlo simulation
     * for a single plan. Returns success rate, terminal balance percentiles
     * (p5–p95), year-by-year fan chart bands and depletion distribution.
     *
     * Volatility / correlation parameters use svc-retire defaults; callers
     * only pick iteration count and (optional) display currency.
     */
    fun runMonteCarlo(
        planId: String,
        iterations: Int = 1000,
        displayCurrency: String? = null
    ): Map<String, Any> =
        post(
            withCurrency(mutableMapOf("iterations" to iterations), displayCurrency),
            "Failed to run Monte Carlo for plan $planId",
            "/projection/plans/{planId}/monte-carlo",
            planId
        )

    /**
     * `POST /composite/projection` — deterministic projection across multiple
     * plan phases. Each phase supplies its own return/inflation/expense
     * parameters; svc-retire stitches them end-to-end. Starting assets come
     * from the first phase's plan.
     */
    fun runCompositeProjection(
        phases: List<CompositePhaseInput>,
        displayCurrency: String
    ): Map<String, Any> =
        post(
            mapOf(DISPLAY_CURRENCY to displayCurrency, PHASES to phases),
            "Failed to run composite projection",
            "/composite/projection"
        )

    /**
     * `POST /composite/scenarios` — scenario comparison across multiple phases.
     */
    fun runCompositeScenarios(
        phases: List<CompositePhaseInput>,
        displayCurrency: String
    ): Map<String, Any> =
        post(
            mapOf(DISPLAY_CURRENCY to displayCurrency, PHASES to phases),
            "Failed to run composite scenarios",
            "/composite/scenarios"
        )

    /**
     * `POST /composite/monte-carlo` — Monte Carlo across multiple phases.
     * A single random economy is sampled across the combined horizon so the
     * phases share a coherent sequence of returns.
     */
    fun runCompositeMonteCarlo(
        phases: List<CompositePhaseInput>,
        displayCurrency: String,
        iterations: Int = 1000
    ): Map<String, Any> =
        post(
            mapOf(DISPLAY_CURRENCY to displayCurrency, PHASES to phases, "iterations" to iterations),
            "Failed to run composite Monte Carlo",
            "/composite/monte-carlo"
        )

    /** [body] with `displayCurrency` added when one was asked for. */
    private fun withCurrency(
        body: MutableMap<String, Any>,
        displayCurrency: String?
    ): Map<String, Any> {
        if (!displayCurrency.isNullOrBlank()) body[DISPLAY_CURRENCY] = displayCurrency
        return body
    }

    /** `POST` [body] to [path] as the caller, or fail with [failure] when svc-retire sends no body. */
    private fun post(
        body: Map<String, Any>,
        failure: String,
        path: String,
        vararg uriVariables: Any
    ): Map<String, Any> =
        restClient
            .post()
            .uri(path, *uriVariables)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(JSON_MAP)
            ?: throw BusinessException(failure)

    private companion object {
        const val DISPLAY_CURRENCY = "displayCurrency"
        const val PHASES = "phases"
    }
}