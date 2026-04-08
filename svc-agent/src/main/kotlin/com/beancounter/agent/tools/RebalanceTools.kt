package com.beancounter.agent.tools

import com.beancounter.agent.clients.RebalanceServiceClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools the LLM can call to answer portfolio-rebalancing questions against
 * svc-rebalance. Exposes read-only endpoints only; creating models, updating
 * plans, approving drafts or calculating live rebalance actions are
 * intentionally *not* exposed — those are write/compute operations that
 * should be driven from the UI with an explicit user gesture.
 *
 * Models and plans are identified by opaque **modelId** / **planId** (UUIDs).
 * Start with [listRebalanceModels] to discover a model id.
 */
@Service
class RebalanceTools(
    private val rebalanceServiceClient: RebalanceServiceClient
) {
    @Tool(description = LIST_MODELS_DESC)
    fun listRebalanceModels(): Map<String, Any> = rebalanceServiceClient.listModels()

    @Tool(description = GET_MODEL_DESC)
    fun getRebalanceModel(
        @ToolParam(description = "Investment model id as returned by listRebalanceModels") modelId: String
    ): Map<String, Any> = rebalanceServiceClient.getModel(modelId)

    @Tool(description = LIST_PLANS_DESC)
    fun listRebalancePlans(
        @ToolParam(description = "Investment model id as returned by listRebalanceModels") modelId: String
    ): Map<String, Any> = rebalanceServiceClient.getPlans(modelId)

    @Tool(description = APPROVED_PLAN_DESC)
    fun getApprovedRebalancePlan(
        @ToolParam(description = "Investment model id as returned by listRebalanceModels") modelId: String
    ): Map<String, Any> = rebalanceServiceClient.getApprovedPlan(modelId)

    @Tool(description = GET_PLAN_DESC)
    fun getRebalancePlan(
        @ToolParam(description = "Investment model id as returned by listRebalanceModels") modelId: String,
        @ToolParam(description = "Plan id as returned by listRebalancePlans") planId: String
    ): Map<String, Any> = rebalanceServiceClient.getPlan(modelId, planId)

    companion object {
        const val LIST_MODELS_DESC =
            "List every investment model the current user owns. An investment model " +
                "defines a target asset allocation (e.g. 60% equities / 40% bonds) and " +
                "is the parent of versioned rebalance plans. Returns each model's id, " +
                "name, description and base currency. Call this first to discover model ids."
        const val GET_MODEL_DESC =
            "Fetch a single investment model by id. Returns the model's target weights, " +
                "composite structure (if it references other models), base currency and " +
                "ownership/transfer state."
        const val LIST_PLANS_DESC =
            "List every versioned plan for a given model. A plan captures the target " +
                "weights + asset prices at a point in time and can be in DRAFT or APPROVED " +
                "state. Returns plan ids, versions, statuses and approval timestamps."
        const val APPROVED_PLAN_DESC =
            "Return the latest APPROVED plan for a model — the authoritative target " +
                "allocation used for actual rebalance calculations. Use this when the " +
                "user asks 'what's the current target for model X'."
        const val GET_PLAN_DESC =
            "Fetch a specific plan version (DRAFT or APPROVED) by its id. Returns the " +
                "plan's target weights per asset, locked prices (if approved), and status."
    }
}
