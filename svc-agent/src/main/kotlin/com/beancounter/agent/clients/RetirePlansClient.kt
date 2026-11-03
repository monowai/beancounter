package com.beancounter.agent.clients

import com.beancounter.auth.TokenService
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.HttpHeaders
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriBuilder
import tools.jackson.module.kotlin.readValue
import java.net.URI

/**
 * Thin read-only client for svc-retire's stored plans: the user's settings,
 * Plans, phases and their financial position. Projections are
 * [RetireProjectionClient]'s.
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
class RetirePlansClient(
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
        val raw = get(MAP_ANY_TYPE, "Failed to retrieve independence settings") { it.path("/settings").build() }

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
            get(MAP_ANY_TYPE, "Failed to retrieve independence plans") { it.path("/independence-plans").build() }

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
        get(JSON_MAP, "Failed to retrieve retirement plans") { it.path("/plans").build() }

    /** `GET /plans/{planId}` — a single retirement plan by id. */
    fun getPlan(planId: String): Map<String, Any> =
        get(JSON_MAP, "Failed to retrieve retirement plan $planId") { it.path("/plans/{planId}").build(planId) }

    /** `GET /plans/{planId}/all-expenses` — plan with working + retirement expenses split. */
    fun getPlanWithExpenses(planId: String): Map<String, Any> =
        get(JSON_MAP, "Failed to retrieve expenses for plan $planId") {
            it.path("/plans/{planId}/all-expenses").build(planId)
        }

    /** `GET /plans/{planId}/contributions` — pension/insurance contributions for a plan. */
    fun getContributions(planId: String): Map<String, Any> =
        get(JSON_MAP, "Failed to retrieve contributions for plan $planId") {
            it.path("/plans/{planId}/contributions").build(planId)
        }

    /**
     * `GET /projection/plans/{planId}/financials` — current financial
     * position (liquid vs non-spendable assets, FI number, FI progress).
     * Optionally converts to a display currency.
     */
    fun getFinancials(
        planId: String,
        displayCurrency: String? = null
    ): Map<String, Any> =
        get(JSON_MAP, "Failed to retrieve financials for plan $planId") { builder ->
            builder
                .path("/projection/plans/{planId}/financials")
                .apply {
                    if (!displayCurrency.isNullOrBlank()) queryParam("displayCurrency", displayCurrency)
                }.build(planId)
        }

    /** `GET` [uri] as the caller, or fail with [failure] when svc-retire sends no body. */
    private fun <T : Any> get(
        type: ParameterizedTypeReference<T>,
        failure: String,
        uri: (UriBuilder) -> URI
    ): T =
        restClient
            .get()
            .uri(uri)
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(type)
            ?: throw BusinessException(failure)

    companion object {
        private val log = LoggerFactory.getLogger(RetirePlansClient::class.java)
        private val objectMapper = BcJson.objectMapper
        private val MAP_ANY_TYPE = object : ParameterizedTypeReference<Map<String, Any?>>() {}

        // Wealth definition and ownership — nothing the model reasons about.
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