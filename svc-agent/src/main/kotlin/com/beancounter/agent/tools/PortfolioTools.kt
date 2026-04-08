package com.beancounter.agent.tools

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Portfolio
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
 */
@Service
class PortfolioTools(
    private val portfolioServiceClient: PortfolioServiceClient
) {
    @Tool(description = LIST_DESC)
    fun listPortfolios(): PortfoliosResponse = portfolioServiceClient.portfolios

    @Tool(description = BY_CODE_DESC)
    fun getPortfolio(
        @ToolParam(description = "Portfolio code as the user types it, e.g. 'TYLER'") code: String
    ): Portfolio = portfolioServiceClient.getPortfolioByCode(code)

    companion object {
        const val LIST_DESC =
            "List every portfolio the current user owns. " +
                "Returns each portfolio's code, name, currency and base currency."
        const val BY_CODE_DESC =
            "Look up a single portfolio by its short user-facing code (e.g. 'TYLER', 'NZD'). " +
                "Returns the portfolio's metadata including currency."
    }
}