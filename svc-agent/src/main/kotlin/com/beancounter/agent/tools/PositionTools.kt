package com.beancounter.agent.tools

import com.beancounter.agent.client.PositionClient
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
 *
 * Responses are scrubbed of absolute monetary amounts: only ratios (weight,
 * XIRR, ROI, change %, yield %) and public price data reach the LLM.
 * See [ResponseScrubber].
 */
@Service
class PositionTools(
    private val positionClient: PositionClient,
    private val scrubber: ResponseScrubber
) {
    @Tool(description = POSITIONS_DESC)
    fun getPositions(
        @ToolParam(description = "Portfolio code as the user types it, e.g. 'TYLER'") portfolioCode: String,
        @ToolParam(description = "Valuation date YYYY-MM-DD or 'today'") asAt: String = "today"
    ): ScrubbedPositionResponse =
        scrubber.scrub(positionClient.getPositionsByCode(portfolioCode, asAt, includeValues = true))

    companion object {
        const val POSITIONS_DESC =
            "Get the positions for a portfolio identified by its code. " +
                "Each position is expressed in privacy-preserving ratios — no absolute " +
                "dollar amounts are returned. Fields per position: " +
                "`weight` (portfolio weight as decimal, e.g. 0.125 = 12.5%), " +
                "`xirr` (annualised money-weighted return, 0.12 = 12% p.a. — most " +
                "accurate measure for holdings over varying periods), " +
                "`roi` (total-return ratio, 1.25 = +25%, 0.85 = −15%), " +
                "`changePercent` (today's price move, decimal), " +
                "`yieldPercent` (dividends / market value, decimal), " +
                "`priceClose`, `priceDate` (public market data), " +
                "`closed` (true if quantity is zero). " +
                "Prefer `xirr` for time-weighted performance comparison across " +
                "holdings; use `roi` for a total-return snapshot. Show both as " +
                "percentages when discussing performance. Never invent or request " +
                "dollar amounts — they are not available."
    }
}