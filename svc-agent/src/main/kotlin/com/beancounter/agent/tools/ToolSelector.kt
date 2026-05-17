package com.beancounter.agent.tools

import org.springframework.stereotype.Service

/**
 * Picks the tool set for a page/domain. Each narrow domain (News, etc.)
 * ships only its own tools; broader pages get a Wealth baseline plus a
 * domain add-on. This keeps the tool-schema portion of the prompt small
 * per call — the LLM sees only what it needs.
 *
 * Domain routing here mirrors [com.beancounter.agent.SystemPromptSelector]
 * so the prompt and the tools always agree.
 */
@Service
class ToolSelector(
    private val portfolioTools: PortfolioTools,
    private val positionTools: PositionTools,
    private val marketTools: MarketTools,
    private val newsTools: NewsTools,
    private val eventTools: EventTools,
    private val retireTools: RetireTools,
    private val rebalanceTools: RebalanceTools
) {
    /** Wealth baseline — portfolios, positions, markets/FX. */
    private val wealthTools: List<Any> by lazy {
        listOf(portfolioTools, positionTools, marketTools)
    }

    fun selectTools(context: Map<String, Any>?): Array<Any> {
        val page = context?.get("page")?.toString()?.lowercase() ?: ""

        return when {
            // Asset Review — single-asset deep dive from assets/lookup.
            // Match before the trade/event branch so the word "asset" doesn't
            // trip a fall-through.
            page.contains("asset review") || page.contains("asset-review") -> {
                (wealthTools + eventTools + newsTools).toTypedArray()
            }
            // News & Sentiment — focused lookup, plus marketTools so the
            // model can ground analyst price targets surfaced in news
            // articles against the current close.
            page.contains("news") && page.contains("sentiment") -> {
                arrayOf(newsTools, marketTools)
            }
            // Independence — wealth + retirement planning.
            page.contains("independence") ||
                page.contains("retire") ||
                page.contains("fi") -> {
                (wealthTools + retireTools).toTypedArray()
            }
            // Rebalance — wealth + rebalance models/plans.
            page.contains("rebalanc") -> {
                (wealthTools + rebalanceTools).toTypedArray()
            }
            // Asset — wealth + corporate events. Users on trade/event pages
            // may also want news on a specific ticker, so keep newsTools.
            page.contains("event") ||
                page.contains("proposed") ||
                page.contains("dividend") ||
                page.contains("trade") ||
                page.contains("trn") -> {
                (wealthTools + eventTools + newsTools).toTypedArray()
            }
            // Wealth — portfolios/holdings. Keep newsTools for "what's
            // happening with my holdings" style questions, plus eventTools
            // so users can ask "how many dividends has GOOG paid?" without
            // having to navigate to a corporate-events page first.
            page.contains("holding") ||
                page.contains("portfolio") ||
                page.contains("wealth") ||
                page.contains("position") -> {
                (wealthTools + eventTools + newsTools).toTypedArray()
            }
            // Unknown / no context — ship everything so the LLM can route.
            else -> {
                (
                    wealthTools +
                        newsTools +
                        eventTools +
                        retireTools +
                        rebalanceTools
                ).toTypedArray()
            }
        }
    }
}