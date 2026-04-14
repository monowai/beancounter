package com.beancounter.agent

import com.beancounter.agent.tools.EventTools
import com.beancounter.agent.tools.MarketTools
import com.beancounter.agent.tools.NewsTools
import com.beancounter.agent.tools.PortfolioTools
import com.beancounter.agent.tools.PositionTools
import com.beancounter.agent.tools.RebalanceTools
import com.beancounter.agent.tools.RetireTools
import org.slf4j.LoggerFactory
import org.springframework.ai.anthropic.AnthropicChatModel
import org.springframework.ai.anthropic.AnthropicChatOptions
import org.springframework.ai.anthropic.api.AnthropicCacheOptions
import org.springframework.ai.anthropic.api.AnthropicCacheStrategy
import org.springframework.ai.anthropic.api.AnthropicCacheTtl
import org.springframework.ai.chat.client.ChatClient
import org.springframework.ai.chat.messages.MessageType
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.ollama.OllamaChatModel
import org.springframework.ai.openai.OpenAiChatModel
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

/**
 * Wires a [ChatClient] from whichever [ChatModel] the active Spring profile
 * exposes, and registers all `@Tool`-bearing beans as default tools.
 *
 * We intentionally split by profile and inject the concrete subtype rather
 * than the `ChatModel` super-type with `@ConditionalOnBean`: Spring Boot
 * evaluates user-config conditions *before* auto-configuration runs, so a
 * condition looking for an auto-configured `ChatModel` bean will always see
 * nothing and the bean is silently skipped. Profile-qualified beans sidestep
 * that ordering trap — activation is driven by the profile, and the specific
 * subtype guarantees there is no ambiguity when both Ollama and OpenAI
 * starters are on the classpath.
 *
 * If neither profile is active, no `ChatClient` bean is created; the agent
 * starts anyway and /agent/query returns 503 "no-llm".
 */
@Configuration
class ChatClientConfiguration {
    private val log = LoggerFactory.getLogger(ChatClientConfiguration::class.java)

    @Bean("chatClient")
    @Profile("ollama")
    fun ollamaChatClient(
        chatModel: OllamaChatModel,
        portfolioTools: PortfolioTools,
        positionTools: PositionTools,
        eventTools: EventTools,
        marketTools: MarketTools,
        retireTools: RetireTools,
        rebalanceTools: RebalanceTools,
        newsTools: NewsTools
    ): ChatClient {
        log.info("Building Ollama ChatClient ({})", chatModel.javaClass.simpleName)
        return build(
            chatModel,
            portfolioTools,
            positionTools,
            eventTools,
            marketTools,
            retireTools,
            rebalanceTools,
            newsTools
        )
    }

    @Bean("chatClient")
    @Profile("openai")
    fun openAiChatClient(
        chatModel: OpenAiChatModel,
        portfolioTools: PortfolioTools,
        positionTools: PositionTools,
        eventTools: EventTools,
        marketTools: MarketTools,
        retireTools: RetireTools,
        rebalanceTools: RebalanceTools,
        newsTools: NewsTools
    ): ChatClient {
        log.info("Building OpenAI ChatClient ({})", chatModel.javaClass.simpleName)
        return build(
            chatModel,
            portfolioTools,
            positionTools,
            eventTools,
            marketTools,
            retireTools,
            rebalanceTools,
            newsTools
        )
    }

    /**
     * Anthropic is the **default** ChatClient — created whenever neither
     * `ollama` nor `openai` is in the active profile list. This matches the
     * agent's `application.yml` defaults (`spring.ai.model.chat: anthropic`)
     * so a developer running with just the `kauri` (or no) profile gets a
     * working Claude-backed agent without having to remember to also add
     * `,anthropic` to `SPRING_PROFILES_ACTIVE`. Activating `ollama` or
     * `openai` explicitly disables this fallback.
     */
    @Bean("chatClient")
    @Profile("!ollama & !openai")
    fun anthropicChatClient(
        chatModel: AnthropicChatModel,
        portfolioTools: PortfolioTools,
        positionTools: PositionTools,
        eventTools: EventTools,
        marketTools: MarketTools,
        retireTools: RetireTools,
        rebalanceTools: RebalanceTools,
        newsTools: NewsTools
    ): ChatClient {
        log.info(
            "Building Anthropic ChatClient ({}) with prompt caching enabled (default)",
            chatModel.javaClass.simpleName
        )

        // Enable Anthropic prompt caching for the static prefix (system
        // prompt + tool definitions) so the multi-iteration tool-calling
        // loop doesn't re-pay for ~4k tokens of unchanged content on every
        // round-trip. First request pays a 1.25× cache-write premium;
        // subsequent requests within the TTL pay 0.1× on cache reads — a
        // net ~40% input-token saving on any query that fires more than
        // one tool call.
        //
        // FIVE_MINUTES TTL matches Anthropic's default and suits ad-hoc
        // chat traffic. Swap to ONE_HOUR if you have sustained scripted
        // workloads hitting the same system prompt; it's more expensive
        // on the write but cheaper over a long session.
        val cacheOptions =
            AnthropicCacheOptions
                .builder()
                .strategy(AnthropicCacheStrategy.SYSTEM_AND_TOOLS)
                .messageTypeTtl(MessageType.SYSTEM, AnthropicCacheTtl.ONE_HOUR)
                .build()

        val anthropicOptions =
            AnthropicChatOptions
                .builder()
                .cacheOptions(cacheOptions)
                .build()

        return ChatClient
            .builder(chatModel)
            .defaultOptions(anthropicOptions)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(
                portfolioTools,
                positionTools,
                eventTools,
                marketTools,
                retireTools,
                rebalanceTools,
                newsTools
            ).build()
    }

    private fun build(
        model: ChatModel,
        portfolioTools: PortfolioTools,
        positionTools: PositionTools,
        eventTools: EventTools,
        marketTools: MarketTools,
        retireTools: RetireTools,
        rebalanceTools: RebalanceTools,
        newsTools: NewsTools
    ): ChatClient =
        ChatClient
            .builder(model)
            .defaultSystem(SYSTEM_PROMPT)
            .defaultTools(
                portfolioTools,
                positionTools,
                eventTools,
                marketTools,
                retireTools,
                rebalanceTools,
                newsTools
            ).build()

    companion object {
        // The agent's mental model of the Beancounter platform.
        // Keep in sync with the tool set — update when you add/remove tools.
        private val SYSTEM_PROMPT =
            """
            # Beancounter Assistant

            Answer from tool calls, never guesses. If a tool fails, say so.

            ## Page context

            User messages may begin with `[Page context: ...]` indicating
            which page of the Holdsworth UI the user is currently viewing.
            Use this to infer intent without asking clarifying questions:
            - If the context mentions a specific portfolio (e.g., page=Holdings,
              description mentions portfolio code), use that portfolio directly.
            - If on the Holdings page, "this portfolio" means the one being viewed.
            - If on Independence, assume composite plan questions.
            - If on Proposed Transactions, focus on pending dividends/events.
            - Never ask "which portfolio?" when the context already tells you.

            **Default to composite.** When the user asks about independence,
            retirement, FI progress, projections, scenarios, or Monte Carlo
            without naming a specific plan, always assume the **composite
            plan** — use `getIndependenceSettings` to load the stored phases
            and run `runComposite*` tools. Only use single-plan tools when
            the user explicitly names one plan. The composite is the user's
            real-world timeline; individual plans are just building blocks.

            ## Services & tools

            - **bc-data** — portfolios, assets, transactions, markets, FX.
              `listPortfolios`, `getPortfolio`, `listMarkets`, `listCurrencies`, `getFxRate`.
            - **bc-position** — live holdings, valuations, P&L from transactions.
              `getPositions`.
            - **bc-event** — corporate actions (dividends, splits).
              `getAssetEvents`, `loadPortfolioEvents`, `backfillPortfolioEvents`.
            - **bc-retire** — retirement planning & analysis. Two-layer model:
              one `UserIndependenceSettings` (user-level common attributes) +
              many plan-level records.
              Read: `getIndependenceSettings`, `listRetirementPlans`,
              `getRetirementPlan`, `getRetirementPlanExpenses`,
              `getRetirementPlanContributions`, `getRetirementFinancials`.
              Compute (side-effect free): `runRetirementProjection`,
              `runRetirementScenarios`, `runRetirementMonteCarlo`,
              `runCompositeRetirementProjection`,
              `runCompositeRetirementScenarios`,
              `runCompositeRetirementMonteCarlo`.
              No CRUD — mutations live in the UI only.
            - **bc-rebalance** — investment models & target allocations.
              `listRebalanceModels`, `getRebalanceModel`, `listRebalancePlans`,
              `getApprovedRebalancePlan`, `getRebalancePlan`. Read-only.
            - **news** — financial news & sentiment via Alpha Vantage.
              `getNews(tickers, topics?)`. Returns headlines, summaries, and
              per-ticker sentiment (Bullish/Bearish/Neutral). Use when users
              ask about news, what's happening with assets, or market sentiment.
              For portfolio-level news, first get positions to find the tickers,
              then call getNews with those tickers.

            Data flows: bc-data (transactions) → bc-position (holdings) →
            bc-event (corporate actions) ↔ bc-data. bc-retire and bc-rebalance
            read from bc-data + bc-position for projections and allocations.

            ## Identifiers

            - **Portfolios**: short codes (`TYLER`, `NZD`). Never ask for UUIDs.
            - **Plans / models**: opaque UUIDs — call `listRetirementPlans` or
              `listRebalanceModels` to discover by name.
            - **Assets**: ticker (`AAPL`). **Dates**: `YYYY-MM-DD` or `today`.
            - **Currencies**: ISO 4217. Pass `displayCurrency` to tools for FX
              conversion — never do FX arithmetic yourself.

            ## UserIndependenceSettings — always prefetch

            Call `getIndependenceSettings` **first** on ANY retirement question.
            It returns the user-level common attributes every plan relies on:

            `currentAge`, `targetIndependenceAge`, `lifeExpectancy`,
            `compositeDisplayCurrency`, `compositePhases` (parsed list of
            `{planId, fromAge, toAge}`), `compositeExcludedPlanIds` (plans
            parked out of the composite), `compositeNarrative` (overarching
            goal across all phases — always read this for composite questions).

            **Never ask the user** for age, retirement age, life expectancy,
            phase boundaries, display currency, or which plans are active
            before calling this tool. Only ask if a field comes back null.

            ## Plan context: `country` & `narrative`

            Each plan may carry `country` (where expenses apply) and
            `narrative` (user-authored context, constraints, assumptions).
            Read both before reasoning about a plan. Use them for:
            - **Disambiguation**: match "my Thailand plan" on name + country +
              narrative keywords; don't ask for UUIDs.
            - **Summaries**: include country + paraphrased narrative alongside
              plan name in every reply.
            - **Constraints**: if the narrative states something the tools
              don't encode, acknowledge it and flag any discrepancy.
            - **Currency**: if no `displayCurrency` specified, prefer the
              currency of the plan's country; fall back to `expensesCurrency`.

            ## Analysis types

            - **Projection** (`runRetirementProjection`) — deterministic
              year-by-year. Answers "will my money last?"
            - **Scenarios** (`runRetirementScenarios`) — Base / Conservative /
              Optimistic / Liquid Only side-by-side.
            - **Monte Carlo** (`runRetirementMonteCarlo`) — stochastic
              simulation. Returns success rate, p5–p95 bands, depletion
              distribution. Default 1000 iterations; don't invent volatility.

            ### Composite (multi-phase)

            `runComposite*` tools stitch multiple plans into one timeline.
            Input: `{planId, fromAge, toAge?}` phases + `displayCurrency`.
            Rules: phases must be chronologically contiguous; `toAge` null on
            last phase; starting assets from first phase's plan. If
            `compositePhases` is already stored in settings, use it directly.
            Skip plans in `compositeExcludedPlanIds`.

            ## Workflow recipes

            For all retirement recipes: `getIndependenceSettings` is always
            Step 1 (omitted below to save space).

            - Portfolio: `getPortfolio(code)` → `getPositions(code)`.
            - Portfolio news: `getPositions(code)` → extract tickers → `getNews(tickers)`.
            - Events: `getAssetEvents(ticker)` or `loadPortfolioEvents(code)`.
            - FX: `getFxRate(from, to)` then multiply.
            - FI progress: `listRetirementPlans` → primary →
              `getRetirementFinancials(id)`. Include country + narrative.
            - Plan by name: `listRetirementPlans` → match name/country →
              `getRetirementPlan(id)` + `getRetirementFinancials(id)`.
            - Expenses: `listRetirementPlans` → `getRetirementPlanExpenses(id)`.
            - Projection: `listRetirementPlans` → `runRetirementProjection(id)`.
            - Scenarios: `listRetirementPlans` → `runRetirementScenarios(id)`.
            - Monte Carlo: `listRetirementPlans` → `runRetirementMonteCarlo(id)`.
            - Composite: use stored `compositePhases` + `compositeDisplayCurrency`
              from settings → `runCompositeRetirementProjection(phases, currency)`
              (or `MonteCarlo` for risk). Read each plan's narrative.
            - Rebalance: `listRebalanceModels` → `getApprovedRebalancePlan(id)`.

            ## Output

            - GitHub-flavored markdown.
            - Round: percentages 1dp, money 2dp, always show currency.
            - Summarise before dumping large result sets.
            - Include plan `country` + paraphrased `narrative` in plan
              summaries.
            - **Chat-first formatting**: responses render in a narrow chat
              panel (~380px). Prefer bullet lists and short paragraphs over
              wide tables. If a table is essential, use at most 2–3 narrow
              columns. Never dump raw tool output as a table.
            - **Don't repeat what's on screen**: when page context tells you
              the user is already viewing data (e.g., holdings, allocations),
              provide *insight and analysis* rather than re-listing the same
              data. Highlight outliers, trends, or actionable observations.

            ## Closed positions

            When retrieving or discussing positions, **exclude closed positions**
            (quantity = 0) by default. Only include them if the user explicitly
            asks about closed positions, sold holdings, or historical trades.
            This applies to portfolio summaries, allocation breakdowns, news
            lookups, and any analysis that starts from position data.

            ## Never

            - Invent data, ids, prices, balances, dates, or simulation
              parameters.
            - Expose UUIDs unless the user explicitly asks for debug info.
            - Offer to create/edit/delete persistent state — direct the user
              to the UI at https://kauri.monowai.com.
            - Guess portfolio codes — if not found, show available options.
            - Build overlapping or non-contiguous composite phases — ask the
              user to clarify age boundaries.
            - Run the same tool twice with same args in one turn.
            - Ask for stored values before calling `getIndependenceSettings`.
            """.trimIndent()
    }
}