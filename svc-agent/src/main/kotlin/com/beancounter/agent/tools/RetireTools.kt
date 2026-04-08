package com.beancounter.agent.tools

import com.beancounter.agent.clients.RetireServiceClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools the LLM can call to answer retirement-planning questions against
 * svc-retire. Exposes read-only endpoints only; plan creation, expense
 * edits and Monte-Carlo scenario runs are deliberately not exposed so the
 * agent cannot mutate a user's retirement plan without explicit UI action.
 *
 * Users identify retirement plans by their opaque **planId** (UUID/string)
 * rather than a friendly code — there is no code concept for plans. If the
 * LLM needs a plan id it should call [listRetirementPlans] first and pick
 * the matching id from the results.
 */
@Service
class RetireTools(
    private val retireServiceClient: RetireServiceClient
) {
    @Tool(description = LIST_DESC)
    fun listRetirementPlans(): Map<String, Any> = retireServiceClient.listPlans()

    @Tool(description = GET_PLAN_DESC)
    fun getRetirementPlan(
        @ToolParam(description = "Retirement plan id as returned by listRetirementPlans") planId: String
    ): Map<String, Any> = retireServiceClient.getPlan(planId)

    @Tool(description = EXPENSES_DESC)
    fun getRetirementPlanExpenses(
        @ToolParam(description = "Retirement plan id as returned by listRetirementPlans") planId: String
    ): Map<String, Any> = retireServiceClient.getPlanWithExpenses(planId)

    @Tool(description = CONTRIBUTIONS_DESC)
    fun getRetirementPlanContributions(
        @ToolParam(description = "Retirement plan id as returned by listRetirementPlans") planId: String
    ): Map<String, Any> = retireServiceClient.getContributions(planId)

    @Tool(description = FINANCIALS_DESC)
    fun getRetirementFinancials(
        @ToolParam(description = "Retirement plan id as returned by listRetirementPlans") planId: String,
        @ToolParam(
            description =
                "Optional ISO currency code (e.g. 'USD', 'NZD') to convert values into. " +
                    "Omit to use the plan's native currency.",
            required = false
        ) displayCurrency: String? = null
    ): Map<String, Any> = retireServiceClient.getFinancials(planId, displayCurrency)

    companion object {
        const val LIST_DESC =
            "List every retirement plan the current user owns. Returns each plan's id, " +
                "name, retirement age target, base currency and primary-plan flag. " +
                "Use this first to discover plan ids before calling any other retirement tool."
        const val GET_PLAN_DESC =
            "Fetch a single retirement plan by its id. Returns plan metadata: owner, " +
                "name, current age, retirement age, life expectancy, inflation rate, " +
                "expected return, base currency, and primary flag."
        const val EXPENSES_DESC =
            "Fetch a retirement plan with its expense breakdown split into working-phase " +
                "and retirement-phase expenses. Use this when the user asks what they " +
                "will spend before or after retirement."
        const val CONTRIBUTIONS_DESC =
            "List pension and insurance contributions attached to a retirement plan " +
                "(e.g. CPF, 401k, KiwiSaver, life policies). Returns each contribution's " +
                "asset code, monthly amount and contributing phase."
        const val FINANCIALS_DESC =
            "Return the plan's current financial position: liquid vs non-spendable asset " +
                "values (resolved from live portfolio data), FI Number (25× annual expenses), " +
                "and FI Progress percentage toward financial independence. Pass a " +
                "displayCurrency to convert the figures away from the plan's native currency."
    }
}
