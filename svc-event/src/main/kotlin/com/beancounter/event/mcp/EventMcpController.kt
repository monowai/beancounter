package com.beancounter.event.mcp

import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST Controller exposing Event MCP Server functionality for AI agents.
 *
 * This controller provides HTTP endpoints that expose the same functionality
 * as the MCP server, making it accessible to AI agents via standard REST calls.
 */
@RestController
@RequestMapping("/mcp")
@Tag(name = "Event MCP", description = "Model Context Protocol endpoints for corporate event management")
class EventMcpController(
    private val eventMcpServer: EventMcpServer
) {
    @GetMapping("/tools")
    @Operation(
        summary = "Get available MCP tools",
        description = "Returns a list of all available MCP tools and their descriptions"
    )
    fun getAvailableTools(): Map<String, String> = eventMcpServer.getAvailableTools()

    @GetMapping("/event/{eventId}")
    @Operation(
        summary = "Get corporate event by ID",
        description = "Retrieve a specific corporate event by its unique identifier"
    )
    fun getEvent(
        @Parameter(description = "Unique identifier for the corporate event")
        @PathVariable eventId: String
    ): CorporateEventResponse = eventMcpServer.getEvent(eventId)

    @GetMapping("/asset/{assetId}/events")
    @Operation(summary = "Get events for asset", description = "Get all corporate events for a specific asset")
    fun getAssetEvents(
        @Parameter(description = "Asset identifier (e.g., asset UUID or ticker)")
        @PathVariable assetId: String
    ): CorporateEventResponses = eventMcpServer.getAssetEvents(assetId)

    @GetMapping("/events/range")
    @Operation(summary = "Get events in date range", description = "Get corporate events within a specific date range")
    fun getEventsInDateRange(
        @Parameter(description = "Start date in YYYY-MM-DD format")
        @RequestParam startDate: String,
        @Parameter(description = "End date in YYYY-MM-DD format")
        @RequestParam endDate: String
    ): CorporateEventResponses = eventMcpServer.getEventsInDateRange(startDate, endDate)

    @GetMapping("/events/scheduled")
    @Operation(
        summary = "Get scheduled events",
        description = "Get scheduled corporate events from a specific start date"
    )
    fun getScheduledEvents(
        @Parameter(description = "Start date in YYYY-MM-DD format")
        @RequestParam startDate: String
    ): CorporateEventResponses = eventMcpServer.getScheduledEvents(startDate)

    @PostMapping("/portfolio/{portfolioId}/load-events")
    @Operation(
        summary = "Load events for portfolio",
        description = "Load corporate events from external sources for a specific portfolio"
    )
    fun loadEventsForPortfolio(
        @Parameter(description = "Portfolio identifier")
        @PathVariable portfolioId: String,
        @Parameter(description = "Start date in YYYY-MM-DD format or 'today'")
        @RequestParam fromDate: String
    ): Map<String, Any> = eventMcpServer.loadEventsForPortfolio(portfolioId, fromDate)

    @PostMapping("/portfolio/{portfolioId}/backfill")
    @Operation(
        summary = "Backfill events",
        description = "Backfill and reprocess existing corporate events for a portfolio"
    )
    fun backfillEvents(
        @Parameter(description = "Portfolio identifier")
        @PathVariable portfolioId: String,
        @Parameter(description = "Start date in YYYY-MM-DD format or 'today'")
        @RequestParam fromDate: String,
        @Parameter(description = "End date in YYYY-MM-DD format (optional)")
        @RequestParam(required = false) toDate: String?
    ): Map<String, Any> = eventMcpServer.backfillEvents(portfolioId, fromDate, toDate)

    @GetMapping("/assets/events")
    @Operation(
        summary = "Get asset events for date",
        description = "Get corporate events for multiple assets on a specific date"
    )
    fun getAssetEventsForDate(
        @Parameter(description = "Comma-separated list of asset identifiers")
        @RequestParam assetIds: String,
        @Parameter(description = "Record date in YYYY-MM-DD format")
        @RequestParam recordDate: String
    ): CorporateEventResponses = eventMcpServer.getAssetEventsForDate(assetIds, recordDate)
}