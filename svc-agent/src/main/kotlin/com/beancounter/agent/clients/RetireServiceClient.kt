package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.annotation.JsonInclude
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import tools.jackson.module.kotlin.readValue

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
    private val tokenService: TokenService,
    private val dateUtils: DateUtils = DateUtils()
) {
    /**
     * `GET /settings` — the user's demographics: year and month of birth,
     * target independence age and life expectancy. Facts about the person,
     * shared by every Plan; the phase timeline is per Plan — see
     * [listIndependencePlans].
     *
     * We compute `currentAge` here so the agent doesn't have to do date math.
     */
    fun getIndependenceSettings(): Map<String, Any?> {
        val raw =
            restClient
                .get()
                .uri("/settings")
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(MAP_ANY_TYPE)
                ?: throw BusinessException("Failed to retrieve independence settings")

        val result = raw.toMutableMap()

        // Compute current age from yearOfBirth (optionally using monthOfBirth)
        (raw["yearOfBirth"] as? Number)?.toInt()?.let { yob ->
            val today = dateUtils.date
            val monthOfBirth = (raw["monthOfBirth"] as? Number)?.toInt()
            val age =
                if (monthOfBirth != null && today.monthValue < monthOfBirth) {
                    today.year - yob - 1
                } else {
                    today.year - yob
                }
            result["currentAge"] = age
        }

        return result
    }

    /**
     * `GET /independence-plans` — the user's Plans. svc-retire calls each an
     * `IndependencePlan`: a whole journey whose `phases` stitch phase plans
     * (the `/plans` resources) into one timeline.
     *
     * Upstream stores `phases` and `excludedPlanIds` as **JSON-encoded
     * strings**. They are decoded here so the tool hands the LLM a structured
     * timeline it can pass straight to the composite endpoints — the model
     * decodes nested JSON unreliably. A Plan with no stored timeline reports
     * empty lists; one whose stored timeline will not decode is also marked
     * `phasesUnreadable`, so the model does not tell the user it is unset.
     *
     * The wealth definition and owner ids are dropped: the model cannot act on
     * them and every token of tool output is billed on every later turn.
     */
    fun listIndependencePlans(): Map<String, Any?> {
        val raw =
            restClient
                .get()
                .uri("/independence-plans")
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(MAP_ANY_TYPE)
                ?: throw BusinessException("Failed to retrieve independence plans")

        val plans =
            (raw["data"] as? List<*>)
                .orEmpty()
                .filterIsInstance<Map<String, Any?>>()
                .map { plan ->
                    val phases = decodeList<Map<String, Any?>>(plan, "phases")
                    val excluded = decodeList<String>(plan, "excludedPlanIds")
                    val unreadable =
                        if (phases.isFailure) mapOf("phasesUnreadable" to true) else emptyMap()
                    plan - PLAN_FIELDS_HIDDEN_FROM_MODEL +
                        mapOf(
                            "phases" to phases.getOrDefault(emptyList()),
                            "excludedPlanIds" to excluded.getOrDefault(emptyList())
                        ) + unreadable
                }
        return mapOf("data" to plans)
    }

    /** Decode a JSON-array-in-a-string field; absent or blank is an empty success, malformed a failure. */
    private inline fun <reified T> decodeList(
        plan: Map<String, Any?>,
        field: String
    ): Result<List<T>> {
        val json =
            (plan[field] as? String)?.takeIf { it.isNotBlank() }
                ?: return Result.success(emptyList())
        return runCatching { objectMapper.readValue<List<T>>(json) }
            .onFailure {
                log.warn(
                    "Failed to parse {} JSON on independence plan {}: {}",
                    field,
                    plan["id"],
                    it.message
                )
            }
    }

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

    // -------- Compute endpoints (side-effect free) --------
    //
    // These POST endpoints are compute-only — they run projections and
    // simulations against a plan's parameters but never persist anything.
    // Safe to expose to the agent even though they're POST (the method is
    // POST because the request bodies can be large, not because they mutate).

    /**
     * `POST /projection/plans/{planId}` — deterministic year-by-year projection
     * for a single plan. Returns runway, depletion age, yearly balances.
     */
    fun runProjection(
        planId: String,
        displayCurrency: String? = null
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>()
        if (!displayCurrency.isNullOrBlank()) body["displayCurrency"] = displayCurrency
        return restClient
            .post()
            .uri("/projection/plans/{planId}", planId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to run projection for plan $planId")
    }

    /**
     * `POST /projection/plans/{planId}/scenarios` — deterministic comparison
     * across Base / Conservative / Optimistic / Liquid-Only scenarios.
     */
    fun runScenarios(
        planId: String,
        displayCurrency: String? = null
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>()
        if (!displayCurrency.isNullOrBlank()) body["displayCurrency"] = displayCurrency
        return restClient
            .post()
            .uri("/projection/plans/{planId}/scenarios", planId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to run scenarios for plan $planId")
    }

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
    ): Map<String, Any> {
        val body = mutableMapOf<String, Any>("iterations" to iterations)
        if (!displayCurrency.isNullOrBlank()) body["displayCurrency"] = displayCurrency
        return restClient
            .post()
            .uri("/projection/plans/{planId}/monte-carlo", planId)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to run Monte Carlo for plan $planId")
    }

    /**
     * `POST /composite/projection` — deterministic projection across multiple
     * plan phases. Each phase supplies its own return/inflation/expense
     * parameters; svc-retire stitches them end-to-end. Starting assets come
     * from the first phase's plan.
     */
    fun runCompositeProjection(
        phases: List<CompositePhaseInput>,
        displayCurrency: String
    ): Map<String, Any> {
        val body =
            mapOf(
                "displayCurrency" to displayCurrency,
                "phases" to phases
            )
        return restClient
            .post()
            .uri("/composite/projection")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to run composite projection")
    }

    /**
     * `POST /composite/scenarios` — scenario comparison across multiple phases.
     */
    fun runCompositeScenarios(
        phases: List<CompositePhaseInput>,
        displayCurrency: String
    ): Map<String, Any> {
        val body =
            mapOf(
                "displayCurrency" to displayCurrency,
                "phases" to phases
            )
        return restClient
            .post()
            .uri("/composite/scenarios")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to run composite scenarios")
    }

    /**
     * `POST /composite/monte-carlo` — Monte Carlo across multiple phases.
     * A single random economy is sampled across the combined horizon so the
     * phases share a coherent sequence of returns.
     */
    fun runCompositeMonteCarlo(
        phases: List<CompositePhaseInput>,
        displayCurrency: String,
        iterations: Int = 1000
    ): Map<String, Any> {
        val body =
            mapOf(
                "displayCurrency" to displayCurrency,
                "phases" to phases,
                "iterations" to iterations
            )
        return restClient
            .post()
            .uri("/composite/monte-carlo")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(body)
            .retrieve()
            .body(MAP_TYPE)
            ?: throw BusinessException("Failed to run composite Monte Carlo")
    }

    companion object {
        private val log = LoggerFactory.getLogger(RetireServiceClient::class.java)
        private val objectMapper = BcJson.objectMapper
        private val MAP_TYPE = object : ParameterizedTypeReference<Map<String, Any>>() {}
        private val MAP_ANY_TYPE = object : ParameterizedTypeReference<Map<String, Any?>>() {}

        /** Wealth definition and ownership — nothing the model reasons about. */
        private val PLAN_FIELDS_HIDDEN_FROM_MODEL =
            setOf(
                "excludedPortfolioIds",
                "liquidatedPortfolioIds",
                "liquidationCostsPercent",
                "manualAssets",
                "ownerId",
                "systemUserId"
            )
    }
}