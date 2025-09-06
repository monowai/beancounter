package com.beancounter.event.mcp

import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import com.beancounter.event.service.BackFillService
import com.beancounter.event.service.EventLoader
import com.beancounter.event.service.EventService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * MCP Server for Beancounter Event Service
 *
 * Exposes corporate event management functionality through the Model Context Protocol
 * for AI integration. Uses actual business services rather than controllers.
 *
 * This implementation provides the business logic that would be exposed via MCP tools.
 * The actual MCP integration will be configured separately once the correct Spring AI MCP API is determined.
 */
@Service
class EventMcpServer(
    private val eventService: EventService,
    private val eventLoader: EventLoader,
    private val backFillService: BackFillService
) {
    private val log = LoggerFactory.getLogger(EventMcpServer::class.java)

    /**
     * Get a specific corporate event by ID
     */
    fun getEvent(eventId: String): CorporateEventResponse {
        log.info("MCP: Getting event with ID: {}", eventId)
        return eventService[eventId]
    }

    /**
     * Get all corporate events for a specific asset
     */
    fun getAssetEvents(assetId: String): CorporateEventResponses {
        log.info("MCP: Getting events for asset: {}", assetId)
        return eventService.getAssetEvents(assetId)
    }

    /**
     * Get corporate events within a specific date range
     */
    fun getEventsInDateRange(
        startDate: String,
        endDate: String
    ): CorporateEventResponses {
        log.info("MCP: Getting events in date range: {} to {}", startDate, endDate)
        val start = LocalDate.parse(startDate)
        val end = LocalDate.parse(endDate)
        val events = eventService.findInRange(start, end)
        return CorporateEventResponses(events)
    }

    /**
     * Get scheduled corporate events from a specific start date
     */
    fun getScheduledEvents(startDate: String): CorporateEventResponses {
        log.info("MCP: Getting scheduled events from: {}", startDate)
        val start = LocalDate.parse(startDate)
        return eventService.getScheduledEvents(start)
    }

    /**
     * Load corporate events from external sources for a specific portfolio
     */
    fun loadEventsForPortfolio(
        portfolioId: String,
        fromDate: String
    ): Map<String, Any> {
        log.info("MCP: Loading events for portfolio: {} from date: {}", portfolioId, fromDate)
        eventLoader.loadEvents(portfolioId, fromDate)
        return mapOf(
            "portfolioId" to portfolioId,
            "fromDate" to fromDate,
            "status" to "loading_started",
            "message" to "Event loading initiated for portfolio $portfolioId from $fromDate"
        )
    }

    /**
     * Backfill and reprocess existing corporate events for a portfolio
     */
    fun backfillEvents(
        portfolioId: String,
        fromDate: String,
        toDate: String? = null
    ): Map<String, Any> {
        val endDate = toDate ?: fromDate
        log.info("MCP: Backfilling events for portfolio: {} from {} to {}", portfolioId, fromDate, endDate)
        backFillService.backFillEvents(portfolioId, fromDate, endDate)
        return mapOf(
            "portfolioId" to portfolioId,
            "fromDate" to fromDate,
            "toDate" to endDate,
            "status" to "backfill_completed",
            "message" to "Event backfill completed for portfolio $portfolioId from $fromDate to $endDate"
        )
    }

    /**
     * Get corporate events for multiple assets on a specific date
     */
    fun getAssetEventsForDate(
        assetIds: String,
        recordDate: String
    ): CorporateEventResponses {
        log.info("MCP: Getting events for assets: {} on date: {}", assetIds, recordDate)
        val assetIdList = assetIds.split(",").map { it.trim() }
        val date = LocalDate.parse(recordDate)
        val events = eventService.find(assetIdList, date)
        return CorporateEventResponses(events)
    }

    /**
     * Get all available MCP tools/functions exposed by this service
     */
    fun getAvailableTools(): Map<String, String> =
        mapOf(
            "get_event" to "Get a specific corporate event by ID",
            "get_asset_events" to "Get all corporate events for a specific asset",
            "get_events_in_date_range" to "Get corporate events within a specific date range",
            "get_scheduled_events" to "Get scheduled corporate events from a specific start date",
            "load_events_for_portfolio" to "Load corporate events from external sources for a specific portfolio",
            "backfill_events" to "Backfill and reprocess existing corporate events for a portfolio",
            "get_asset_events_for_date" to "Get corporate events for multiple assets on a specific date"
        )
}