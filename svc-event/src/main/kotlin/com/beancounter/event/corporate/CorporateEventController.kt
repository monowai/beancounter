package com.beancounter.event.corporate

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.event.CorporateEvent
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.distribution.EventDistribution
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Stored corporate events: read, create, delete, and reprocess a single event into
 * transactions for the portfolios that held the asset on its record date.
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
class CorporateEventController(
    private val eventStore: CorporateEventStore,
    private val eventDistribution: EventDistribution,
    private val dateUtils: DateUtils
) {
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
    ): CorporateEventResponse = eventStore[eventId]

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
                responseCode = "400",
                description = "Malformed payDate"
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
        @PathVariable eventId: String,
        @Parameter(
            description =
                "Optional override pay date (YYYY-MM-DD). " +
                    "If provided, bypasses the default recordDate + 18 days calculation",
            example = "2024-12-10"
        )
        @RequestParam(required = false) payDate: String? = null
    ): CorporateEventResponse {
        val overridePayDate = payDate?.let { dateUtils.getDate(it).toString() }
        val event = eventStore[eventId]
        eventDistribution.processEvent(event.data, overridePayDate)
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
    ): CorporateEventResponses = eventStore.getAssetEvents(assetId)

    @DeleteMapping(
        value = ["/asset/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Delete all corporate events for a specific asset",
        description = """
            Deletes all stored corporate events for a specific asset.
            Use this to clean up incorrect events (e.g., wrong-company data from symbol collisions).
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Events deleted successfully"
            )
        ]
    )
    fun deleteAssetEvents(
        @Parameter(
            description = "Asset identifier",
            example = "GNE-NZX"
        )
        @PathVariable assetId: String
    ): Map<String, Any> {
        val count = eventStore.deleteForAsset(assetId)
        return mapOf(
            "assetId" to assetId,
            "deleted" to count
        )
    }

    @PostMapping(
        value = ["/events"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a corporate event",
        description = """
            Creates a new corporate event (dividend, split) directly.
            The event is saved idempotently - if an event already exists for the same
            asset and record date, the existing event is returned.

            Use this for manually backfilling historical events from external data sources.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "Event created or already exists"
            )
        ]
    )
    fun createEvent(
        @RequestBody event: CorporateEvent
    ): CorporateEventResponse = CorporateEventResponse(eventStore.save(event))

    @GetMapping(
        value = ["/events/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get corporate events for an asset",
        description = """
            Retrieves corporate events for a specific asset, optionally filtered by date range.
            Supply both fromDate and toDate to filter by record date, or neither to return
            all events for the asset. Supplying only one is rejected with 400.

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
                responseCode = "400",
                description = "Invalid date format, or only one of fromDate and toDate supplied"
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
            description = "Start date for filtering events (YYYY-MM-DD format, optional)",
            example = "2024-01-01"
        )
        @RequestParam(required = false) fromDate: String? = null,
        @Parameter(
            description = "End date for filtering events (YYYY-MM-DD format, optional)",
            example = "2024-01-31"
        )
        @RequestParam(required = false) toDate: String? = null
    ): CorporateEventResponses {
        if (fromDate == null && toDate == null) {
            return eventStore.getAssetEvents(assetId)
        }
        if (fromDate == null || toDate == null) {
            throw BusinessException("fromDate and toDate must be supplied together")
        }
        return CorporateEventResponses(
            eventStore.findInRange(assetId, dateUtils.getDate(fromDate), dateUtils.getDate(toDate))
        )
    }
}