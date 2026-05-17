package com.beancounter.agent.tools

import com.beancounter.client.services.PortfolioServiceClient
import org.springframework.ai.tool.annotation.Tool
import org.springframework.ai.tool.annotation.ToolParam
import org.springframework.stereotype.Service

/**
 * Tools the LLM can call to enumerate and resolve Beancounter portfolios.
 *
 * The default surface is by **code** — the user-visible short identifier (e.g.
 * `TYLER`). A parallel by-id tool ([getPortfolioByPortfolioId]) is exposed
 * for managed/shared portfolios where the caller is not the owner: portfolio
 * code is unique only within an owner, so the by-code lookup 404s for
 * advisers viewing a client's portfolio. The frontend exposes `portfolioId`
 * in the page context whenever the by-id tool should be picked.
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

    @Tool(description = BY_ID_DESC)
    fun getPortfolioByPortfolioId(
        @ToolParam(
            description =
                "Internal portfolio id (UUID-like, ~22 chars, e.g. 'WKN0stp6QIWhg8LdQti5-Q'). " +
                    "Required for managed/shared portfolios where the user is not the owner. " +
                    "Use this when the page context provides `portfolioId` instead of `portfolioCode`."
        ) portfolioId: String
    ): ScrubbedPortfolio = scrubber.scrub(portfolioServiceClient.getPortfolioById(portfolioId))

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
        const val BY_ID_DESC =
            "Same response shape as getPortfolio, but resolves the portfolio by " +
                "its internal id rather than its code. Prefer this tool whenever " +
                "the page context exposes `portfolioId` (managed/shared portfolios). " +
                "The by-code tool 404s for shared portfolios because portfolio " +
                "code is unique only within an owner."
    }
}