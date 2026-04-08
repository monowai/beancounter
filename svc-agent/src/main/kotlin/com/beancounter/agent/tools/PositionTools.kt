package com.beancounter.agent.tools

import com.beancounter.agent.client.PositionClient
import com.beancounter.common.contracts.PositionResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools the LLM uses to retrieve portfolio positions and valuations.
 *
 * Derived analyses (largest holdings, top movers, performance commentary) are
 * intentionally NOT exposed as separate tools — the LLM can sort and filter
 * the position list itself once it has the data. Keeping the tool surface
 * narrow reduces prompt-token cost and tool-selection errors.
 */
@Service
class PositionTools(
    private val positionClient: PositionClient
) {
    @Tool(description = POSITIONS_DESC)
    fun getPositions(
        @ToolParam(description = "Portfolio code as the user types it, e.g. 'TYLER'") portfolioCode: String,
        @ToolParam(description = "Valuation date YYYY-MM-DD or 'today'") asAt: String = "today"
    ): PositionResponse = positionClient.getPositionsByCode(portfolioCode, asAt, includeValues = true)

    companion object {
        const val POSITIONS_DESC =
            "Get the positions and valuations for a portfolio identified by its code. " +
                "Returns each holding with quantity, cost, market value, gain/loss and currency. " +
                "Use this for 'show my holdings', 'what are my largest positions', 'analyze my portfolio', etc."
    }
}