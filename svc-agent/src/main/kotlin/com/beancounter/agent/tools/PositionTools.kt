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

    @Tool(description = POSITIONS_BY_ID_DESC)
    fun getPositionsByPortfolioId(
        @ToolParam(
            description =
                "Internal portfolio id (UUID-like, ~22 chars, e.g. 'WKN0stp6QIWhg8LdQti5-Q'). " +
                    "Required for managed/shared portfolios where the user is not the owner. " +
                    "Use this when the page context provides `portfolioId` instead of `portfolioCode`."
        ) portfolioId: String,
        @ToolParam(description = "Valuation date YYYY-MM-DD or 'today'") asAt: String = "today"
    ): ScrubbedPositionResponse =
        scrubber.scrub(positionClient.getPositionsById(portfolioId, asAt, includeValues = true))

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
                "`opened` (ISO date YYYY-MM-DD the position was opened — use this " +
                "to age the holding before commenting on returns; a position " +
                "opened in the last few days will legitimately show near-zero " +
                "XIRR or change); " +
                "`lastTrade` (ISO date of the most recent transaction — useful " +
                "for detecting dormant holdings); " +
                "`lastDividend` (ISO date of the most recent dividend, or null " +
                "if the holding has never paid a dividend). " +
                "Closed (zero-quantity) positions are filtered out before " +
                "the response is built, so every row represents an open " +
                "holding — there is no `closed` column to inspect. " +
                "The response also carries `portfolioCode`, `portfolioName`, " +
                "`baseCurrency`, `asAt`, `mixedCurrencies`, and `overallIrr`. " +
                "Show ratios as percentages when discussing performance. " +
                "Never invent or request dollar amounts, ROI, dividend yield, or " +
                "trade currency — they are not available in this tool."
        const val POSITIONS_BY_ID_DESC =
            "Same response shape as getPositions, but resolves the portfolio by " +
                "its internal id rather than its code. Prefer this tool whenever " +
                "the page context exposes `portfolioId` (managed/shared portfolios). " +
                "The by-code tool 404s for shared portfolios because portfolio " +
                "code is unique only within an owner."
    }
}