package com.beancounter.event.controller

import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import com.beancounter.event.contract.CorporateEventResponses
import com.beancounter.event.service.BackFillService
import com.beancounter.event.service.CorporateEventStore
import com.beancounter.event.service.EventLoader
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

private const val STATUS = "status"
private const val ACCEPTED = "accepted"
private const val COMPLETED = "completed"
private const val PORTFOLIO_ID = "portfolioId"
private const val PORTFOLIOS = "portfolios"
private const val FROM_DATE = "fromDate"
private const val TO_DATE = "toDate"
private const val AS_AT = "asAt"

/**
 * Corporate event ingest: fetches events from external market data sources (load) and
 * replays stored events into portfolios as transactions (backfill).
 *
 * The workflow typically runs load first, then backfill.
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
class CorporateEventIngestController(
    private val backFillService: BackFillService,
    private val eventLoader: EventLoader,
    private val eventStore: CorporateEventStore,
    private val portfolioService: PortfolioServiceClient,
    private val dateUtils: DateUtils
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @PostMapping(
        value = ["/backfill/{portfolioId}"],
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
    fun backFillPortfolio(
        @Parameter(
            description = "Unique identifier of the portfolio to process",
            example = "portfolio-123"
        )
        @PathVariable portfolioId: String,
        @Parameter(
            description = "Start date for event processing (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-01"
        )
        @RequestParam(required = false) fromDate: String = DateUtils.TODAY,
        @Parameter(
            description = "End date for event processing (YYYY-MM-DD format, defaults to fromDate)",
            example = "2024-01-31"
        )
        @RequestParam(required = false) toDate: String = DateUtils.TODAY
    ): Map<String, Any> {
        backFillService.backFillEvents(
            portfolioId,
            fromDate,
            toDate
        )
        return mapOf(
            STATUS to ACCEPTED,
            PORTFOLIO_ID to portfolioId,
            FROM_DATE to fromDate,
            TO_DATE to toDate
        )
    }

    @PostMapping(
        value = ["/backfill"],
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
        @RequestParam(required = false) fromDate: String = DateUtils.TODAY,
        @Parameter(
            description = "End date for event processing (YYYY-MM-DD format, defaults to fromDate)",
            example = "2024-01-31"
        )
        @RequestParam(required = false) toDate: String = DateUtils.TODAY
    ): Map<String, Any> {
        eventLoader.loadEvents(fromDate)
        val portfolios = portfolioService.portfolios
        log.info("Backfill events for portfolioCount: ${portfolios.data.size}, from: $fromDate, to: $toDate")
        val processed = mutableListOf<String>()
        for (portfolio in portfolios.data) {
            log.info("BackFilling ${portfolio.code}, $fromDate to $toDate")
            backFillService.backFillEvents(
                portfolio.id,
                fromDate,
                toDate
            )
            processed.add(portfolio.code)
        }
        return mapOf(
            STATUS to COMPLETED,
            FROM_DATE to fromDate,
            TO_DATE to toDate,
            PORTFOLIOS to processed
        )
    }

    @PostMapping(
        value = ["/load"],
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
        @RequestParam(required = false) fromDate: String = DateUtils.TODAY
    ): Map<String, Any> {
        eventLoader.loadEvents(fromDate)
        return mapOf(
            STATUS to COMPLETED,
            FROM_DATE to fromDate,
            PORTFOLIOS to portfolioService.portfolios.data.size
        )
    }

    @PostMapping(
        value = ["/load/{portfolioId}"],
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
            description = "Unique identifier of the portfolio",
            example = "portfolio-123"
        )
        @PathVariable portfolioId: String,
        @Parameter(
            description = "Date for loading events (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(required = false) asAt: String = DateUtils.TODAY
    ): Map<String, Any> {
        eventLoader.loadEvents(
            portfolioId,
            asAt
        )
        return mapOf(
            STATUS to ACCEPTED,
            PORTFOLIO_ID to portfolioId,
            AS_AT to asAt
        )
    }

    @PostMapping(
        value = ["/load/asset/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Load corporate events for a specific asset",
        description = """
            Fetches new corporate events from external data sources for a specific asset.
            This endpoint:

            * Retrieves corporate events for the specified asset from external sources
            * Stores the events in the local database
            * Returns all events for the asset
            * Does NOT require a portfolio - works directly with asset ID

            Use this when you want to load events for a single asset.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Events loaded and returned"
            )
        ]
    )
    fun loadAssetEvents(
        @Parameter(
            description = "Asset identifier (e.g., AAPL, SGOV)",
            example = "AAPL"
        )
        @PathVariable assetId: String,
        @Parameter(
            description = "Date for loading events (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(required = false) asAt: String = DateUtils.TODAY
    ): CorporateEventResponses {
        val date = dateUtils.getDate(asAt)
        eventLoader.loadEventsForAsset(assetId, date)
        // Return all events for this asset after loading
        return eventStore.getAssetEvents(assetId)
    }
}