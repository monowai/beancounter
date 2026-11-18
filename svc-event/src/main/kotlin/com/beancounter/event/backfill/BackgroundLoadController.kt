package com.beancounter.event.backfill

import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.utils.DateUtils
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** Answer to a background load request. */
data class BackgroundLoadResponse(
    val status: BackgroundLoadStatus,
    val portfolioId: String,
    val asAt: String
)

/**
 * Starts a portfolio's corporate event load (load, then backfill) in the background
 * and answers at once. The transactions it creates reach svc-data asynchronously.
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
class BackgroundLoadController(
    private val backgroundEventLoad: BackgroundEventLoad,
    private val portfolioService: PortfolioServiceClient,
    private val dateUtils: DateUtils
) {
    @PostMapping(
        value = ["/load/{portfolioId}/background"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Start loading corporate events for a portfolio in the background",
        description = """
            Resolves the portfolio with the caller's token, then loads corporate events
            for its holdings from asAt to today and backfills them, on a background thread.
            Returns 202 at once. Created transactions reach svc-data asynchronously.
            While the portfolio's load is still running, a further request returns
            ALREADY_RUNNING and starts nothing.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "202", description = "Load started, or already running"),
            ApiResponse(responseCode = "400", description = "asAt is after today"),
            ApiResponse(responseCode = "403", description = "Portfolio not accessible"),
            ApiResponse(responseCode = "404", description = "Portfolio not found")
        ]
    )
    fun loadPortfolioEventsInBackground(
        @Parameter(description = "Unique identifier of the portfolio", example = "portfolio-123")
        @PathVariable portfolioId: String,
        @Parameter(
            description = "First date to load events from, up to today (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(required = false) asAt: String = DateUtils.TODAY
    ): ResponseEntity<BackgroundLoadResponse> {
        dateUtils.requireNotAfterToday("asAt", asAt)
        val portfolio = portfolioService.getPortfolioById(portfolioId)
        val status = backgroundEventLoad.start(portfolio, asAt)
        return ResponseEntity.accepted().body(BackgroundLoadResponse(status, portfolio.id, asAt))
    }
}