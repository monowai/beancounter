package com.beancounter.agent.tools

import com.beancounter.agent.clients.CompositePhaseInput
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
    @Tool(description = SETTINGS_DESC)
    fun getIndependenceSettings(): Map<String, Any?> = retireServiceClient.getIndependenceSettings()

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

    // -------- Compute tools (side-effect free) --------
    //
    // These tools run projections and simulations against a plan's parameters
    // without persisting anything. They are safe to call whenever the user
    // wants to understand "how will my plan play out" or "what's the risk of
    // running out of money".

    @Tool(description = PROJECTION_DESC)
    fun runRetirementProjection(
        @ToolParam(description = "Retirement plan id as returned by listRetirementPlans") planId: String,
        @ToolParam(
            description = "Optional ISO currency to convert results into; omit for plan's native currency.",
            required = false
        ) displayCurrency: String? = null
    ): Map<String, Any> = retireServiceClient.runProjection(planId, displayCurrency)

    @Tool(description = SCENARIOS_DESC)
    fun runRetirementScenarios(
        @ToolParam(description = "Retirement plan id as returned by listRetirementPlans") planId: String,
        @ToolParam(
            description = "Optional ISO currency to convert results into; omit for plan's native currency.",
            required = false
        ) displayCurrency: String? = null
    ): Map<String, Any> = retireServiceClient.runScenarios(planId, displayCurrency)

    @Tool(description = MONTE_CARLO_DESC)
    fun runRetirementMonteCarlo(
        @ToolParam(description = "Retirement plan id as returned by listRetirementPlans") planId: String,
        // NOTE: typed as Int? (not Int with a default) because Spring AI
        // reflectively invokes tool methods and Kotlin default parameters
        // don't survive that reflection path. If the LLM omits iterations,
        // the method would receive null → boxing a null into primitive int
        // throws NPE. Accepting Int? and defaulting in the body is the safe
        // shape for tool methods that expose "optional with a default".
        @ToolParam(
            description =
                "Number of simulation iterations (100–10000, default 1000). Higher = more " +
                    "precise percentiles but slower. Omit or pass null to use the default.",
            required = false
        ) iterations: Int? = null,
        @ToolParam(
            description = "Optional ISO currency to convert results into; omit for plan's native currency.",
            required = false
        ) displayCurrency: String? = null
    ): Map<String, Any> = retireServiceClient.runMonteCarlo(planId, iterations ?: 1000, displayCurrency)

    @Tool(description = COMPOSITE_PROJECTION_DESC)
    fun runCompositeRetirementProjection(
        @ToolParam(
            description =
                "Ordered list of retirement phases. Each phase is {planId, fromAge, toAge?}. " +
                    "Leave toAge null on the final phase to run until death. Phases must be " +
                    "age-contiguous (phase N's toAge = phase N+1's fromAge)."
        ) phases: List<CompositePhaseInput>,
        @ToolParam(
            description =
                "ISO currency the combined projection should be reported in " +
                    "(e.g. 'USD', 'SGD'). Required."
        ) displayCurrency: String
    ): Map<String, Any> = retireServiceClient.runCompositeProjection(phases, displayCurrency)

    @Tool(description = COMPOSITE_SCENARIOS_DESC)
    fun runCompositeRetirementScenarios(
        @ToolParam(
            description =
                "Ordered list of retirement phases {planId, fromAge, toAge?}. Leave toAge " +
                    "null on the final phase."
        ) phases: List<CompositePhaseInput>,
        @ToolParam(description = "ISO currency to report the scenarios in.") displayCurrency: String
    ): Map<String, Any> = retireServiceClient.runCompositeScenarios(phases, displayCurrency)

    @Tool(description = COMPOSITE_MONTE_CARLO_DESC)
    fun runCompositeRetirementMonteCarlo(
        @ToolParam(
            description =
                "Ordered list of retirement phases {planId, fromAge, toAge?}. Leave toAge " +
                    "null on the final phase."
        ) phases: List<CompositePhaseInput>,
        @ToolParam(description = "ISO currency to report the simulation in.") displayCurrency: String,
        // See note on runRetirementMonteCarlo — Int? + body default is the
        // reflection-safe shape for optional primitive params in Spring AI tools.
        @ToolParam(
            description =
                "Number of simulation iterations (100–10000, default 1000). Omit or pass " +
                    "null to use the default.",
            required = false
        ) iterations: Int? = null
    ): Map<String, Any> = retireServiceClient.runCompositeMonteCarlo(phases, displayCurrency, iterations ?: 1000)

    companion object {
        const val SETTINGS_DESC =
            "Return the user's stored independence settings — the COMMON " +
                "ATTRIBUTES that apply to EVERY retirement plan this user owns. " +
                "These are the foundational inputs to every projection, scenario, " +
                "Monte Carlo and composite analysis: " +
                "yearOfBirth + computed currentAge (using monthOfBirth for accuracy), " +
                "targetIndependenceAge (target FI age), " +
                "lifeExpectancy (planning horizon upper bound), " +
                "compositeDisplayCurrency (preferred output currency), " +
                "compositePhases (the user's pre-configured composite phase list, " +
                "already parsed into {planId, fromAge, toAge} objects), and " +
                "compositeExcludedPlanIds (plans the user has parked out of the " +
                "composite — the inactive one of an alternative-scenario pair). " +
                "CALL THIS FIRST on ANY retirement question — not just composite " +
                "ones. It is the user-level context that every individual plan " +
                "relies on. A single-plan FI progress question needs currentAge " +
                "and lifeExpectancy just as much as a composite does. The answers " +
                "to 'what's your current age', 'when do you want to retire', " +
                "'how long should I plan for' and 'what ages do you transition " +
                "between plans' are already stored — do not ask the user for " +
                "values this tool can return."
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
        const val PROJECTION_DESC =
            "Run a deterministic year-by-year projection of a retirement plan using its " +
                "stored expected return, inflation and expense assumptions. Returns runway " +
                "(months/years until funds deplete), depletion age, surplus/deficit vs " +
                "target balance, and year-by-year balance evolution. Use this when the user " +
                "asks 'will my money last' or 'how long will my savings last'."
        const val SCENARIOS_DESC =
            "Run a deterministic scenario comparison: Base Case (plan assumptions), " +
                "Conservative (lower returns, higher inflation), Optimistic (higher returns, " +
                "lower inflation), and Liquid Only (excluding housing equity). Use this when " +
                "the user asks 'what if returns are worse' or wants to see a risk/reward spread."
        const val MONTE_CARLO_DESC =
            "Run a stochastic Monte Carlo simulation on a single retirement plan to estimate " +
                "the probability of NOT running out of money. Returns success rate (% of " +
                "iterations that survived the horizon), terminal balance percentiles (p5 " +
                "through p95), year-by-year fan chart bands, and the age-at-depletion " +
                "distribution. Use this when the user asks about 'probability of success', " +
                "'risk of running out', 'worst case scenario' or similar uncertainty questions."
        const val COMPOSITE_PROJECTION_DESC =
            "Run a deterministic projection across MULTIPLE retirement phases stitched end " +
                "to end. Each phase references a different plan with its own expenses, " +
                "income and return assumptions, letting the user model life transitions " +
                "(e.g. 'while working in Singapore until 50, then semi-retired in NZ 50–65, " +
                "then full retirement 65+'). Starting assets come from the first phase's " +
                "plan — the portfolio is shared across all phases. Phases must be " +
                "age-contiguous and in chronological order."
        const val COMPOSITE_SCENARIOS_DESC =
            "Scenario comparison (Base / Conservative / Optimistic / Liquid Only) across " +
                "multiple retirement phases. Use this when the user wants to stress-test a " +
                "multi-phase plan (e.g. 'what if my pre-retire equity returns are lower but " +
                "my post-retire bond returns are higher')."
        const val COMPOSITE_MONTE_CARLO_DESC =
            "Monte Carlo simulation across multiple retirement phases. Each phase contributes " +
                "its own economic parameters (return, inflation, volatility, expenses, income) " +
                "and a single random economy is sampled across the combined horizon so the " +
                "phases share a coherent sequence of returns. Returns success rate, terminal " +
                "balance percentiles, fan chart bands and depletion age distribution across " +
                "the full multi-phase timeline. Use this for the most realistic risk estimate " +
                "on a multi-phase plan."
    }
}
