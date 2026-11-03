package com.beancounter.marketdata.trn

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.contracts.PositionMoveResponse
import com.beancounter.common.contracts.TrnDeleteResponse
import com.beancounter.common.contracts.TrnGroupBy
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.contracts.TrnStatusUpdateRequest
import com.beancounter.common.contracts.TrnStatusUpdateResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.TrnStatus
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * One asset's trade history: its trades and corporate events, in one portfolio or across them.
 */
@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Transaction Management",
    description = "Operations for managing financial transactions including trades, dividends, and corporate actions"
)
class AssetTradeHistoryController(
    private val trnQueryService: TrnQueryService,
    private val portfolioService: PortfolioService
) {
    @GetMapping(
        value = ["/{portfolioId}/asset/{assetId}/events"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get corporate events for asset in portfolio",
        description = """
            Retrieves all corporate events (dividends, splits, etc.) for a specific asset in a portfolio.

            Use this to:
            * View dividend history for an asset
            * Track corporate actions
            * Analyze event impact on portfolio
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Corporate events retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio or asset not found"
            )
        ]
    )
    fun findAssetEvents(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Asset identifier",
            example = "AAPL"
        ) @PathVariable("assetId") assetId: String
    ): TrnResponse =
        TrnResponse(
            trnQueryService.findEvents(
                portfolioId,
                assetId
            )
        )

    @GetMapping(
        value = ["/{portfolioId}/asset/{assetId}/trades"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get trade transactions for asset in portfolio",
        description = """
            Retrieves all trade transactions (buys, sells) for a specific asset in a portfolio.

            Use this to:
            * View trading history for an asset
            * Analyze trading patterns
            * Track position changes over time
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Trade transactions retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio or asset not found"
            )
        ]
    )
    fun findAssetTrades(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Asset identifier",
            example = "AAPL"
        ) @PathVariable("assetId") assetId: String
    ): TrnResponse {
        val trns =
            trnQueryService.findAssetTrades(
                portfolioId,
                assetId
            )
        return TrnResponse(
            trns,
            TrnTradeSummaryBuilder.build(trns, TrnGroupBy.BROKER)
        )
    }

    @GetMapping(
        value = ["/asset/{assetId}/trades"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get trade transactions for an asset across multiple portfolios",
        description = """
            Retrieves trade transactions for a single asset across several portfolios,
            used by the aggregated holdings drill-down where an asset is held in more
            than one portfolio. Each transaction carries its portfolio so the caller
            can group the union by portfolio.

            The request fails closed: if any supplied portfolio is unknown or not
            viewable by the caller, the whole request is rejected rather than
            returning a partial result.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Trade transactions retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "A portfolio or asset was not found"
            )
        ]
    )
    fun findAssetTradesAcrossPortfolios(
        @Parameter(
            description = "Asset identifier",
            example = "AAPL"
        ) @PathVariable("assetId") assetId: String,
        @Parameter(
            description = "Comma-separated portfolio identifiers",
            example = "portfolio-123,portfolio-456"
        ) @RequestParam("portfolios") portfolios: String
    ): TrnResponse {
        val trns =
            trnQueryService.findAssetTrades(
                portfolios.split(",").map { it.trim() }.filter { it.isNotBlank() },
                assetId
            )
        return TrnResponse(
            trns,
            TrnTradeSummaryBuilder.build(trns, TrnGroupBy.PORTFOLIO)
        )
    }

    @PostMapping(
        value = ["/query"]
    )
    @Operation(
        summary = "Query transactions by asset and date",
        description = """
            Queries transactions for a specific asset and date range.
            This endpoint provides flexible transaction searching.

            Use this to:
            * Search for specific transactions
            * Filter transactions by date range
            * Find transactions matching specific criteria
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Query results retrieved successfully"
            ), ApiResponse(
                responseCode = "400",
                description = "Invalid query parameters"
            )
        ]
    )
    fun findByAsset(
        @Parameter(
            description = "Query parameters for transaction search"
        ) @RequestBody query: TrustedTrnQuery
    ): TrnResponse =
        TrnResponse(
            trnQueryService.findAssetTrades(
                portfolioService.find(query.portfolio.id),
                query.assetId,
                query.tradeDate
            )
        )
}