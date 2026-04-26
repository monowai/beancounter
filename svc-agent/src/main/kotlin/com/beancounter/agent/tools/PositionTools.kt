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
 * Responses are scrubbed of absolute monetary amounts: only weight, xirr,
 * change %, and public price data reach the LLM, in a compact columnar
 * shape. See [ResponseScrubber] / [ScrubbedPositionResponse].
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
                "Response is columnar to minimise tokens: `cols` lists field names " +
                "in order; each entry of `rows` is an array of values aligned to " +
                "`cols` (rows[i][j] is the value of cols[j] for the i-th position). " +
                "No absolute dollar amounts are returned. Columns: " +
                "`assetCode`, `assetName`, `market` (public identifiers); " +
                "`priceClose` (public market price); " +
                "`changePercent` (today's price move, decimal — 0.012 = +1.2%); " +
                "`xirr` (annualised money-weighted return, decimal — 0.12 = 12% p.a.; " +
                "the most accurate performance measure across holdings); " +
                "`weight` (portfolio weight, decimal — 0.125 = 12.5%); " +
                "`category` (asset class, useful for grouping); " +
                "`closed` (boolean — true means zero quantity). " +
                "Never mention, count, or discuss closed positions in your " +
                "answer unless the user has explicitly asked about closed, " +
                "sold, or historical holdings — filter them out silently. " +
                "The response also carries `portfolioCode`, `portfolioName`, " +
                "`baseCurrency`, `asAt`, `mixedCurrencies`, and `overallIrr`. " +
                "Show ratios as percentages when discussing performance. " +
                "Never invent or request dollar amounts, ROI, dividend yield, or " +
                "trade currency — they are not available in this tool."
    }
}