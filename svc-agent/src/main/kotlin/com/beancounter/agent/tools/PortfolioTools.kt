package com.beancounter.agent.tools

import com.beancounter.client.services.PortfolioServiceClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools the LLM can call to enumerate and resolve Beancounter portfolios.
 *
 * Every portfolio-shaped tool in the agent accepts a user-visible **code**,
 * never an internal UUID — users only ever talk about codes, and surfacing
 * the id to the LLM just invites it to feed codes into id-shaped parameters.
 * Code→id resolution, when needed, happens inside the tool implementation.
 *
 * Responses are scrubbed of absolute monetary amounts (market value, gain
 * on day) — the LLM receives IRR as a ratio and portfolio metadata only.
 * See [ResponseScrubber].
 */
@Service
class PortfolioTools(
    private val portfolioServiceClient: PortfolioServiceClient,
    private val scrubber: ResponseScrubber
) {
    @Tool(description = LIST_DESC)
    fun listPortfolios(): List<ScrubbedPortfolio> = scrubber.scrub(portfolioServiceClient.portfolios)

    @Tool(description = BY_CODE_DESC)
    fun getPortfolio(
        @ToolParam(description = "Portfolio code as the user types it, e.g. 'TYLER'") code: String
    ): ScrubbedPortfolio = scrubber.scrub(portfolioServiceClient.getPortfolioByCode(code))

    companion object {
        const val LIST_DESC =
            "List every portfolio the current user owns. " +
                "Returns each portfolio's code, name, trading currency, base currency, " +
                "and XIRR (annualised return as a decimal ratio, e.g. 0.12 = 12% p.a.). " +
                "Absolute monetary amounts are intentionally not included."
        const val BY_CODE_DESC =
            "Look up a single portfolio by its short user-facing code (e.g. 'TYLER', 'NZD'). " +
                "Returns the portfolio's metadata and XIRR as a decimal ratio. " +
                "Absolute monetary amounts are intentionally not included."
    }
}