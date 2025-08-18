package com.beancounter.event.controller

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.config.EventControllerConfig
import com.beancounter.event.contract.CorporateEventResponse
import com.beancounter.event.contract.CorporateEventResponses
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Corporate Event Management Controller
 *
 * This controller manages corporate actions (dividends, splits, etc.) for investment portfolios.
 * It provides endpoints for:
 * - Loading new corporate events from external data sources
 * - Processing existing events to generate transactions
 * - Querying stored corporate events
 *
 * The workflow typically involves:
 * 1. Loading events from external sources (load endpoints)
 * 2. Processing events to create transactions (backfill endpoints)
 * 3. Querying events for analysis (query endpoints)
 */
@RestController
@RequestMapping
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Corporate Events",
    description = "Endpoints for managing corporate actions (dividends, splits) and generating transactions"
)
class EventController(
    private val config: EventControllerConfig
) {
    private val eventService = config.serviceConfig.eventService
    private val backFillService = config.serviceConfig.backFillService
    private val eventLoader = config.serviceConfig.eventLoader
    private val portfolioService = config.sharedConfig.portfolioService
    private val dateUtils = config.sharedConfig.dateUtils

    private val log = LoggerFactory.getLogger(this::class.java)

    @PostMapping(
        value = ["/backfill/{portfolioId}/{fromDate}/{toDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Process corporate events for a specific portfolio",
        description = """
            Processes existing corporate events (dividends, splits) for a specific portfolio 
            and generates the corresponding transactions. This endpoint:
            
            * Finds all corporate events that occurred between the specified dates
            * Identifies which assets in the portfolio were affected
            * Generates dividend or split transactions for each affected position
            * Returns immediately (asynchronous processing)
            
            Use this when you want to process events for a single portfolio.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Processing accepted and started"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    operator fun get(
        @Parameter(
            description = "Unique identifier of the portfolio to process",
            example = "portfolio-123"
        )
        @PathVariable portfolioId: String,
        @Parameter(
            description = "Start date for event processing (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-01"
        )
        @PathVariable(required = false) fromDate: String = DateUtils.TODAY,
        @Parameter(
            description = "End date for event processing (YYYY-MM-DD format, defaults to fromDate)",
            example = "2024-01-31"
        )
        @PathVariable(required = false) toDate: String = DateUtils.TODAY
    ) = backFillService.backFillEvents(
        portfolioId,
        fromDate,
        toDate
    )

    @PostMapping(
        value = ["/backfill/{fromDate}/{toDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Process corporate events for all portfolios",
        description = """
            Processes existing corporate events for ALL portfolios in the system and generates 
            the corresponding transactions. This endpoint:
            
            * First loads any missing events from external sources for the date range
            * Then processes events for each portfolio in the system
            * Generates dividend or split transactions for affected positions
            * Processes portfolios sequentially
            
            Use this for bulk processing across all portfolios.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Processing completed for all portfolios"
            )
        ]
    )
    fun backFillPortfolios(
        @Parameter(
            description = "Start date for event processing (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-01"
        )
        @PathVariable(required = false) fromDate: String = DateUtils.TODAY,
        @Parameter(
            description = "End date for event processing (YYYY-MM-DD format, defaults to fromDate)",
            example = "2024-01-31"
        )
        @PathVariable(required = false) toDate: String = DateUtils.TODAY
    ) {
        eventLoader.loadEvents(fromDate)
        val portfolios = portfolioService.portfolios
        log.info("Backfill events for portfolioCount: ${portfolios.data.size}, from: $fromDate, to: $toDate")
        for (portfolio in portfolios.data) {
            log.info("BackFilling ${portfolio.code}, $fromDate to $toDate")
            backFillService.backFillEvents(
                portfolio.id,
                fromDate,
                toDate
            )
        }
    }

    @PostMapping(
        value = ["/load/{fromDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Load corporate events from external sources for all portfolios",
        description = """
            Fetches new corporate events (dividends, splits) from external data sources 
            for all portfolios in the system. This endpoint:
            
            * Connects to external market data providers
            * Retrieves corporate events for assets held in portfolios
            * Stores the events in the local database
            * Processes events from the specified date to today
            
            This is typically the first step in the corporate event workflow.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Events loaded successfully"
            )
        ]
    )
    fun loadEvents(
        @Parameter(
            description = "Start date for loading events (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-01"
        )
        @PathVariable(required = false) fromDate: String = DateUtils.TODAY
    ) = eventLoader.loadEvents(fromDate)

    @PostMapping(
        value = ["/load/{portfolioId}/{asAtDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Load corporate events for a specific portfolio",
        description = """
            Fetches new corporate events from external data sources for a specific portfolio.
            This endpoint:
            
            * Identifies assets held in the specified portfolio
            * Retrieves corporate events for those assets from external sources
            * Stores the events in the local database
            * Processes events for the specified date
            
            Use this when you want to load events for a single portfolio.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Event loading accepted and started"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun loadPortfolioEvents(
        @Parameter(
            description = "Date for loading events (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @PathVariable(required = false) asAtDate: String = DateUtils.TODAY,
        @Parameter(
            description = "Unique identifier of the portfolio",
            example = "portfolio-123"
        )
        @PathVariable portfolioId: String
    ) {
        eventLoader.loadEvents(
            portfolioId,
            asAtDate
        )
    }

    @GetMapping(
        value = ["/{eventId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get a specific corporate event by ID",
        description = """
            Retrieves a single corporate event by its unique identifier.
            
            Use this to:
            * Get details of a specific corporate event
            * Verify event information
            * Retrieve event for processing
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Corporate event retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Event not found"
            )
        ]
    )
    fun getEvent(
        @Parameter(
            description = "Unique identifier of the corporate event",
            example = "event-123"
        )
        @PathVariable eventId: String
    ): CorporateEventResponse = eventService[eventId]

    @PostMapping(
        value = ["/{eventId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Reprocess a corporate event",
        description = """
            Retrieves a corporate event by ID and reprocesses it to generate transactions.
            This endpoint:
            
            * Finds the event by its unique identifier
            * Processes the event to generate dividend or split transactions
            * Returns the event details
            * Returns immediately (asynchronous processing)
            
            Use this to reprocess events that may have failed or need to be regenerated.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Event reprocessing accepted and started"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Event not found"
            )
        ]
    )
    fun reprocessEvent(
        @Parameter(
            description = "Unique identifier of the corporate event to reprocess",
            example = "event-123"
        )
        @PathVariable eventId: String
    ): CorporateEventResponse {
        val event = eventService[eventId]
        eventService.processEvent(event.data)
        return event
    }

    @GetMapping(
        value = ["/asset/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all corporate events for a specific asset",
        description = """
            Retrieves all stored corporate events (dividends, splits) for a specific asset.
            Returns events ordered by payment date (most recent first).
            
            This is useful for:
            * Analyzing dividend history for an asset
            * Checking split events
            * Auditing corporate actions
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Corporate events retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found or no events exist"
            )
        ]
    )
    fun getAssetEvents(
        @Parameter(
            description = "Asset identifier (e.g., ticker symbol)",
            example = "AAPL"
        )
        @PathVariable assetId: String
    ): CorporateEventResponses = eventService.getAssetEvents(assetId)

    @GetMapping(
        value = ["/events/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all corporate events for a specific asset (alternative endpoint)",
        description = """
            Alternative endpoint to retrieve all stored corporate events for a specific asset.
            This endpoint provides the same functionality as /asset/{assetId}.
            
            Returns events ordered by payment date (most recent first).
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Corporate events retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Sample Response",
                                value = """
                                {
                                  "data": [
                                    {
                                      "id": "event-123",
                                      "assetId": "AAPL",
                                      "trnType": "DIVI",
                                      "recordDate": "2024-01-15",
                                      "payDate": "2024-02-01",
                                      "rate": 0.24
                                    }
                                  ]
                                }
                                """
                            )
                        ]
                    )
                ]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found or no events exist"
            )
        ]
    )
    fun getEvents(
        @Parameter(
            description = "Asset identifier (e.g., ticker symbol)",
            example = "AAPL"
        )
        @PathVariable assetId: String
    ): CorporateEventResponses = eventService.getAssetEvents(assetId)

    @GetMapping(
        value = ["/events/{assetId}/{fromDate}/{toDate}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get corporate events for an asset within a date range",
        description = """
            Retrieves corporate events for a specific asset within the specified date range.
            This endpoint filters events by both asset and date range.
            
            Use this for:
            * Analyzing events in a specific time period
            * Generating reports for specific dates
            * Auditing corporate actions within a timeframe
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Corporate events retrieved successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid date format"
            )
        ]
    )
    fun getEvents(
        @Parameter(
            description = "Asset identifier (e.g., ticker symbol)",
            example = "AAPL"
        )
        @PathVariable assetId: String,
        @Parameter(
            description = "Start date for filtering events (YYYY-MM-DD format)",
            example = "2024-01-01"
        )
        @PathVariable fromDate: String,
        @Parameter(
            description = "End date for filtering events (YYYY-MM-DD format)",
            example = "2024-01-31"
        )
        @PathVariable toDate: String
    ): CorporateEventResponses {
        val startDate = dateUtils.getDate(fromDate)
        val endDate = dateUtils.getDate(toDate)
        val events =
            eventService
                .findInRange(startDate, endDate)
                .filter { it.assetId == assetId }
        return CorporateEventResponses(events)
    }
}