package com.beancounter.agent.tools

import org.springframework.stereotype.Service

/**
 * Selects relevant tools based on page context to minimize
 * token usage. Core tools are always included; domain-specific
 * tools are added based on the user's current page.
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
    private val coreTools: List<Any> by lazy {
        listOf(portfolioTools, positionTools, marketTools, newsTools)
    }

    fun selectTools(context: Map<String, Any>?): Array<Any> {
        val page = context?.get("page")?.toString()?.lowercase() ?: ""
        val tools = coreTools.toMutableList()

        when {
            page.contains("independence") || page.contains("retire") -> {
                tools.add(retireTools)
            }
            page.contains("rebalanc") -> {
                tools.add(rebalanceTools)
            }
            page.contains("proposed") || page.contains("event") -> {
                tools.add(eventTools)
            }
            page.isEmpty() -> {
                // No context — include everything
                tools.addAll(listOf(eventTools, retireTools, rebalanceTools))
            }
        }

        return tools.toTypedArray()
    }
}