package com.beancounter.agent.tools

import com.beancounter.agent.isFxPair
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
    private val holdingTools: HoldingTools,
    private val marketTools: MarketTools,
    private val newsTools: NewsTools,
    private val macroTools: MacroTools,
    private val portfolioEventTools: EventTools,
    private val assetEventTools: AssetEventTools,
    private val retireTools: RetireTools,
    private val rebalanceTools: RebalanceTools,
    private val watchlistTools: WatchlistTools
) {
    /** Wealth baseline — portfolios, positions (and one holding, open or sold), markets/FX, macro backdrop. */
    private val wealthTools: List<Any> by lazy {
        listOf(portfolioTools, positionTools, holdingTools, marketTools, macroTools)
    }

    /** Corporate events: one asset's history plus loading them for the caller's portfolios. */
    private val eventTools: List<Any> by lazy { listOf(assetEventTools, portfolioEventTools) }

    fun selectTools(context: Map<String, Any>?): Array<Any> {
        val page = context?.get("page")?.toString()?.lowercase() ?: ""

        return when {
            // Holding Analysis — the caller's own holding of one asset: the holding,
            // position and portfolio tools, plus the asset's market, macro, events and news.
            // Matched first, so "holding" never falls through to Wealth.
            page.contains("holding analysis") || page.contains("holding-analysis") -> {
                arrayOf(
                    portfolioTools,
                    positionTools,
                    holdingTools,
                    marketTools,
                    macroTools,
                    assetEventTools,
                    newsTools
                )
            }
            // Watchlist — tracked assets, not holdings: no portfolio or position tools.
            page.contains("watchlist") -> {
                arrayOf(watchlistTools, marketTools, newsTools, macroTools)
            }
            // Asset Review — single-asset deep dive from assets/lookup.
            // Match before the trade/event branch so the word "asset" doesn't
            // trip a fall-through.
            page.contains("asset review") || page.contains("asset-review") -> {
                // About the asset alone, never the caller's portfolios or holdings,
                // so one review serves every user (and is cached that way).
                // A currency pair has no corporate events: the rate, macro and news.
                if (isFxPair(context)) {
                    arrayOf(marketTools, macroTools, newsTools)
                } else {
                    arrayOf(marketTools, macroTools, assetEventTools, newsTools)
                }
            }
            // News & Sentiment — focused lookup, plus marketTools so the
            // model can ground analyst price targets surfaced in news
            // articles against the current close, and macroTools so it can
            // quantify the backdrop behind the headlines it's reading.
            page.contains("news") && page.contains("sentiment") -> {
                arrayOf(newsTools, marketTools, macroTools)
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
                        rebalanceTools +
                        watchlistTools
                ).toTypedArray()
            }
        }
    }
}