package com.beancounter.agent.tools

import com.beancounter.agent.clients.CompositePhaseInput
import com.beancounter.agent.clients.RetireServiceClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools the LLM can call to answer independence-planning questions against
 * svc-retire. Exposes read-only endpoints only; plan creation and expense
 * edits are deliberately not exposed so the agent cannot mutate a user's
 * plan without explicit UI action.
 *
 * Vocabulary: the user's **Plan** is svc-retire's `IndependencePlan` — a whole
 * journey, listed by [listIndependencePlans]. A **phase** is one stage of it,
 * which svc-retire stores as a `RetirementPlan`; every `*RetirementPlan*` tool
 * here, and every `planId` parameter, addresses a phase. The names predate the
 * journey and are kept so tool-call history stays readable.
 */
@Service
class RetireTools(
    private val retireServiceClient: RetireServiceClient,
    /**
     * Shrinks the compute tools' year-by-year payloads before they enter the
     * conversation — see [ProjectionCompactor] for the measured failure that
     * made this necessary.
     */
    private val compactor: ProjectionCompactor
) {
    @Tool(description = SETTINGS_DESC)
    fun getIndependenceSettings(): Map<String, Any?> = retireServiceClient.getIndependenceSettings()

    @Tool(description = LIST_INDEPENDENCE_PLANS_DESC)
    fun listIndependencePlans(): Map<String, Any?> = retireServiceClient.listIndependencePlans()

    @Tool(description = LIST_DESC)
    fun listRetirementPlans(): Map<String, Any> = retireServiceClient.listPlans()

    @Tool(description = GET_PLAN_DESC)
    fun getRetirementPlan(
        @ToolParam(description = PHASE_ID_PARAM) planId: String
    ): Map<String, Any> = retireServiceClient.getPlan(planId)

    @Tool(description = EXPENSES_DESC)
    fun getRetirementPlanExpenses(
        @ToolParam(description = PHASE_ID_PARAM) planId: String
    ): Map<String, Any> = retireServiceClient.getPlanWithExpenses(planId)

    @Tool(description = CONTRIBUTIONS_DESC)
    fun getRetirementPlanContributions(
        @ToolParam(description = PHASE_ID_PARAM) planId: String
    ): Map<String, Any> = retireServiceClient.getContributions(planId)

    @Tool(description = FINANCIALS_DESC)
    fun getRetirementFinancials(
        @ToolParam(description = PHASE_ID_PARAM) planId: String,
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
        @ToolParam(description = PHASE_ID_PARAM) planId: String,
        @ToolParam(
            description = "Optional ISO currency to convert results into; omit for plan's native currency.",
            required = false
        ) displayCurrency: String? = null
    ): Map<String, Any> = compact(retireServiceClient.runProjection(planId, displayCurrency))

    @Tool(description = SCENARIOS_DESC)
    fun runRetirementScenarios(
        @ToolParam(description = PHASE_ID_PARAM) planId: String,
        @ToolParam(
            description = "Optional ISO currency to convert results into; omit for plan's native currency.",
            required = false
        ) displayCurrency: String? = null
    ): Map<String, Any> = compact(retireServiceClient.runScenarios(planId, displayCurrency))

    @Tool(description = MONTE_CARLO_DESC)
    fun runRetirementMonteCarlo(
        @ToolParam(description = PHASE_ID_PARAM) planId: String,
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
    ): Map<String, Any> = compact(retireServiceClient.runMonteCarlo(planId, iterations ?: 1000, displayCurrency))

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
    ): Map<String, Any> = compact(retireServiceClient.runCompositeProjection(phases, displayCurrency))

    @Tool(description = COMPOSITE_SCENARIOS_DESC)
    fun runCompositeRetirementScenarios(
        @ToolParam(
            description =
                "Ordered list of retirement phases {planId, fromAge, toAge?}. Leave toAge " +
                    "null on the final phase."
        ) phases: List<CompositePhaseInput>,
        @ToolParam(description = "ISO currency to report the scenarios in.") displayCurrency: String
    ): Map<String, Any> = compact(retireServiceClient.runCompositeScenarios(phases, displayCurrency))

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
    ): Map<String, Any> =
        compact(
            retireServiceClient.runCompositeMonteCarlo(phases, displayCurrency, iterations ?: 1000)
        )

    /**
     * Shrink a compute-tool payload for the conversation. The cast is confined
     * here: [ProjectionCompactor] is honest that a JSON payload can hold nulls,
     * while Spring AI's tool signatures are `Map<String, Any>` — and a null
     * value must stay null rather than be dropped, since "never depletes" and
     * "no depletion age reported" are different answers.
     */
    @Suppress("UNCHECKED_CAST")
    private fun compact(raw: Map<String, Any>): Map<String, Any> = compactor.compact(raw) as Map<String, Any>

    companion object {
        const val PHASE_ID_PARAM =
            "Phase id — a planId from an independence Plan's `phases` (listIndependencePlans), " +
                "or an id from listRetirementPlans"
        const val SETTINGS_DESC =
            "Return the user's stored independence settings — demographics that " +
                "apply to EVERY Plan and phase: yearOfBirth and monthOfBirth, " +
                "computed currentAge, targetIndependenceAge (target FI age) and " +
                "lifeExpectancy (planning horizon upper bound). " +
                "CALL THIS FIRST on ANY independence question. The answers to " +
                "'what's your current age', 'when do you want to retire' and " +
                "'how long should I plan for' are already stored — do not ask " +
                "the user for values this tool can return. Phase boundaries and " +
                "display currency are NOT here — they belong to each Plan, see " +
                "listIndependencePlans."
        const val LIST_INDEPENDENCE_PLANS_DESC =
            "List the user's independence Plans — each a WHOLE journey, the thing " +
                "the user means by 'my plan'. Each has `phases`: the ordered timeline " +
                "of {planId, fromAge, toAge}, where planId is a PHASE id usable with " +
                "the *RetirementPlan* tools and the list is passed straight into " +
                "runCompositeRetirementProjection / Scenarios / MonteCarlo. " +
                "`isPrimary` marks the user's default Plan; `displayCurrency` is the " +
                "currency for composite runs; `excludedPlanIds` are phases parked " +
                "out of the timeline. `phasesUnreadable: true` means a stored timeline " +
                "failed to load — it is NOT unset. Also returns the Plan's name and its " +
                "return, inflation, fee and tax assumption rates."
        const val LIST_DESC =
            "List every phase (stored as a 'retirement plan') the current user owns, " +
                "across all their independence Plans. Returns each phase's id, name, " +
                "country, narrative, `expensesCurrency`, assumptions and " +
                "`independencePlanId` — the independence Plan it belongs to. Use it " +
                "to resolve a phase by name or to read phase details; the phase " +
                "ORDER and ages come from listIndependencePlans."
        const val GET_PLAN_DESC =
            "Fetch a single phase of an independence Plan by its phase id (planId). " +
                "Returns the phase's name, country, narrative, monthly expenses and " +
                "`expensesCurrency`, return / inflation / fee assumptions, asset allocation, " +
                "pension and working income, and `independencePlanId`."
        const val EXPENSES_DESC =
            "Fetch one phase of an independence Plan (by phase id) with its expense breakdown " +
                "split into working and retirement expenses. Use this when the user asks what they " +
                "will spend before or after retirement."
        const val CONTRIBUTIONS_DESC =
            "List pension and insurance contributions attached to one phase of an independence Plan " +
                "(e.g. CPF, 401k, KiwiSaver, life policies). Returns each contribution's " +
                "asset code, monthly amount and contributing phase."
        const val FINANCIALS_DESC =
            "Return one phase's current financial position: liquid vs non-spendable asset " +
                "values (resolved from live portfolio data), FI Number (25× annual expenses), " +
                "and FI Progress percentage toward financial independence. Pass a " +
                "displayCurrency to convert the figures away from the phase's native currency."
        const val PROJECTION_DESC =
            "Run a deterministic year-by-year projection of ONE phase in isolation using its " +
                "stored expected return, inflation and expense assumptions. Returns runway " +
                "(months/years until funds deplete), depletion age, surplus/deficit vs " +
                "target balance, and year-by-year balance evolution. Use this when the user " +
                "asks 'will my money last' or 'how long will my savings last'."
        const val SCENARIOS_DESC =
            "Run a deterministic scenario comparison for ONE phase in isolation: Base Case (its assumptions), " +
                "Conservative (lower returns, higher inflation), Optimistic (higher returns, " +
                "lower inflation), and Liquid Only (excluding housing equity). Use this when " +
                "the user asks 'what if returns are worse' or wants to see a risk/reward spread."
        const val MONTE_CARLO_DESC =
            "Run a stochastic Monte Carlo simulation on ONE phase in isolation to estimate " +
                "the probability of NOT running out of money. Returns success rate (% of " +
                "iterations that survived the horizon), terminal balance percentiles (p5 " +
                "through p95), year-by-year fan chart bands, and the age-at-depletion " +
                "distribution. Use this when the user asks about 'probability of success', " +
                "'risk of running out', 'worst case scenario' or similar uncertainty questions."
        const val COMPOSITE_PROJECTION_DESC =
            "Run a deterministic projection of a whole independence Plan: its phases stitched " +
                "end to end. Each phase carries its own expenses, " +
                "income and return assumptions, letting the user model life transitions " +
                "(e.g. 'while working in Singapore until 50, then semi-retired in NZ 50–65, " +
                "then full retirement 65+'). Starting assets come from the first " +
                "phase — the portfolio is shared across all phases. Phases must be " +
                "age-contiguous and in chronological order."
        const val COMPOSITE_SCENARIOS_DESC =
            "Scenario comparison (Base / Conservative / Optimistic / Liquid Only) across " +
                "a whole independence Plan (all its phases). Use this when the user wants to " +
                "stress-test the Plan (e.g. 'what if my pre-retire equity returns are lower but " +
                "my post-retire bond returns are higher')."
        const val COMPOSITE_MONTE_CARLO_DESC =
            "Monte Carlo simulation across a whole independence Plan. Each phase contributes " +
                "its own economic parameters (return, inflation, volatility, expenses, income) " +
                "and a single random economy is sampled across the combined horizon so the " +
                "phases share a coherent sequence of returns. Returns success rate, terminal " +
                "balance percentiles, fan chart bands and depletion age distribution across " +
                "the full multi-phase timeline. Use this for the most realistic risk estimate " +
                "on the Plan."
    }
}