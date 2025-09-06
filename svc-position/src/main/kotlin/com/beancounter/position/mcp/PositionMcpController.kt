package com.beancounter.position.mcp

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller exposing Position MCP Server functionality for AI agents.
 *
 * This controller provides HTTP endpoints that expose portfolio position and valuation
 * functionality for AI agent integration.
 */
@RestController
@RequestMapping("/mcp")
@Tag(name = "Position MCP", description = "Model Context Protocol endpoints for portfolio positions and valuations")
class PositionMcpController(
    private val positionMcpServer: PositionMcpServer
) {
    @GetMapping("/tools")
    @Operation(
        summary = "Get available MCP tools",
        description = "Returns a list of all available MCP tools and their descriptions"
    )
    fun getAvailableTools(): Map<String, String> = positionMcpServer.getAvailableTools()

    @PostMapping("/portfolio/positions")
    @Operation(summary = "Get portfolio positions", description = "Get portfolio positions with valuation")
    fun getPositions(
        @RequestBody portfolio: Portfolio,
        @Parameter(description = "Valuation date in YYYY-MM-DD format or 'today'")
        @RequestParam(defaultValue = "today") valuationDate: String,
        @Parameter(description = "Include market values in response")
        @RequestParam(defaultValue = "true") includeValues: Boolean
    ): PositionResponse = positionMcpServer.getPositions(portfolio, valuationDate, includeValues)

    @PostMapping("/query")
    @Operation(summary = "Query positions", description = "Build positions from a custom query")
    fun queryPositions(
        @RequestBody query: TrustedTrnQuery
    ): PositionResponse = positionMcpServer.queryPositions(query)

    @PostMapping("/portfolio/build")
    @Operation(summary = "Build positions", description = "Build positions for a portfolio and date")
    fun buildPositions(
        @RequestBody portfolio: Portfolio,
        @Parameter(description = "Valuation date in YYYY-MM-DD format or 'today'")
        @RequestParam(defaultValue = "today") valuationDate: String
    ): PositionResponse = positionMcpServer.buildPositions(portfolio, valuationDate)

    @PostMapping("/value")
    @Operation(summary = "Value positions", description = "Value existing positions (add market values)")
    fun valuePositions(
        @RequestBody positionResponse: PositionResponse
    ): PositionResponse = positionMcpServer.valuePositions(positionResponse)

    @PostMapping("/portfolio/metrics")
    @Operation(summary = "Get portfolio metrics", description = "Get portfolio performance metrics")
    fun getPortfolioMetrics(
        @RequestBody portfolio: Portfolio,
        @Parameter(description = "Valuation date in YYYY-MM-DD format or 'today'")
        @RequestParam(defaultValue = "today") valuationDate: String
    ): Map<String, Any> = positionMcpServer.getPortfolioMetrics(portfolio, valuationDate)

    @PostMapping("/portfolio/breakdown")
    @Operation(summary = "Get position breakdown", description = "Get detailed position breakdown for a portfolio")
    fun getPositionBreakdown(
        @RequestBody portfolio: Portfolio,
        @Parameter(description = "Valuation date in YYYY-MM-DD format or 'today'")
        @RequestParam(defaultValue = "today") valuationDate: String
    ): Map<String, Any> = positionMcpServer.getPositionBreakdown(portfolio, valuationDate)
}