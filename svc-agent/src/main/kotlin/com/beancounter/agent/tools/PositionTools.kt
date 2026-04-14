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
                "Each position includes quantity, cost basis, market value, gain/loss, " +
                "and two return metrics: " +
                "ROI (simple return = marketValue / costBasis, expressed as a ratio — " +
                "1.25 means +25% total return, 0.85 means −15%) and " +
                "XIRR (annualised money-weighted internal rate of return that accounts " +
                "for the timing and size of each cash flow — buys, sells, dividends — " +
                "making it the most accurate performance measure for positions held over " +
                "varying periods. Expressed as a decimal, e.g. 0.12 = 12% p.a.). " +
                "When the user asks about 'returns', 'performance', or 'how well is X doing', " +
                "prefer XIRR for time-weighted comparison across holdings, and ROI for a " +
                "quick total-return snapshot. Always show both when discussing performance."
    }
}