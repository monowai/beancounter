package com.beancounter.marketdata.trn

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PositionMoveRequest
import com.beancounter.common.contracts.PositionMoveResponse
import com.beancounter.common.contracts.TrnDeleteResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.utils.DateUtils
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
 * Core CRUD controller for transactions: create, read, update, delete, and moving a position
 * between portfolios.
 *
 * @see TrnSettlementController for proposed and settled trns and the cash ladder
 * @see AssetTradeHistoryController for an asset's trades and events
 * @see TrnAnalysisController for analysis and reporting endpoints
 * @see TrnExportController for CSV export endpoints
 * @see TrnBrokerController for broker-related endpoints
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
class TrnController(
    var trnService: TrnService,
    var trnFinder: TrnFinder,
    var dateUtils: DateUtils,
    var positionMoveService: PositionMoveService
) {
    @GetMapping(
        value = ["/portfolio/{portfolioId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(AuthConstants.USER_WIDGET_OR_SYSTEM)
    @Operation(
        summary = "Get transactions for portfolio as of specific date",
        description = """
            Retrieves all transactions for a portfolio as of a specific date.
            If no date is provided, uses today's date.

            Use this to:
            * View portfolio transactions for a specific date
            * Get transaction history for reporting
            * Analyze portfolio activity over time
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transactions retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Portfolio Transactions",
                                value = """
                                {
                                  "data": [
                                    {
                                      "id": "trn-123",
                                      "assetId": "AAPL",
                                      "trnType": "BUY",
                                      "quantity": 100,
                                      "tradeDate": "2024-01-15",
                                      "price": 150.25
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
                description = "Portfolio not found"
            )
        ]
    )
    fun findAsAt(
        @PathVariable @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) portfolioId: String,
        @Parameter(
            description = "Date to retrieve transactions for (YYYY-MM-DD format)",
            example = "2024-01-15"
        ) @RequestParam(required = false) asAt: String? = null
    ): TrnResponse =
        TrnResponse(
            trnFinder.findForPortfolio(
                portfolioId,
                dateUtils.getFormattedDate(asAt ?: dateUtils.today())
            )
        )

    @GetMapping(value = ["/{trnId}"])
    @Operation(
        summary = "Get transaction by ID",
        description = """
            Retrieves a specific transaction by its unique identifier.

            Use this to:
            * Get detailed information about a specific transaction
            * View transaction details for auditing or reporting
            * Access transaction metadata and history
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transaction retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Transaction not found"
            )
        ]
    )
    fun find(
        @PathVariable @Parameter(
            description = "Unique transaction identifier",
            example = "trn-123"
        ) trnId: String
    ): TrnResponse =
        TrnResponse(
            trnFinder.getPortfolioTrn(
                trnId
            )
        )

    @PostMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Create new transactions",
        description = """
            Creates new transactions for a portfolio.
            This endpoint handles bulk transaction creation efficiently.

            Use this to:
            * Record new trades or corporate actions
            * Import transactions from external systems
            * Create multiple transactions in one request
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transactions created successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Transaction Creation Response",
                                value = """
                                {
                                  "data": [
                                    {
                                      "id": "trn-123",
                                      "assetId": "AAPL",
                                      "trnType": "BUY",
                                      "quantity": 100,
                                      "tradeDate": "2024-01-15",
                                      "price": 150.25
                                    }
                                  ]
                                }
                                """
                            )
                        ]
                    )
                ]
            ), ApiResponse(
                responseCode = "400",
                description = "Invalid transaction data"
            )
        ]
    )
    fun update(
        @Parameter(
            description = "Transaction request containing portfolio and transaction data"
        ) @RequestBody trnRequest: TrnRequest
    ): TrnResponse {
        val result = trnService.saveWithResult(trnRequest.portfolioId, trnRequest)
        return TrnResponse(result.trns, result.warnings)
    }

    @PatchMapping(
        value = ["/{portfolioId}/{trnId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Update existing transaction",
        description = """
            Updates an existing transaction with new data.
            Only the provided fields will be updated.

            Use this to:
            * Correct transaction details
            * Update transaction metadata
            * Modify transaction attributes
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transaction updated successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Transaction not found"
            )
        ]
    )
    fun patch(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Transaction identifier to update",
            example = "trn-123"
        ) @PathVariable("trnId") trnId: String,
        @Parameter(
            description = "Updated transaction data"
        ) @RequestBody trnInput: TrnInput
    ): TrnResponse =
        trnService.patch(
            portfolioId,
            trnId,
            trnInput
        )

    @DeleteMapping(value = ["/portfolio/{portfolioId}"])
    @Operation(
        summary = "Delete all transactions for a portfolio",
        description = """
            Permanently deletes all transactions for a specific portfolio.
            This operation cannot be undone.

            Use this to:
            * Clear all transaction history for a portfolio
            * Remove portfolio data for privacy or compliance
            * Reset portfolio transaction state
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transactions deleted successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Delete Count",
                                value = "42"
                            )
                        ]
                    )
                ]
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun purge(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String
    ): Long = trnService.purge(portfolioId)

    @DeleteMapping(
        value = ["/{trnId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Delete specific transaction",
        description = """
            Permanently deletes a specific transaction.
            This operation cannot be undone.

            Use this to:
            * Remove incorrect transactions
            * Delete duplicate entries
            * Clean up transaction data
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transaction deleted successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Transaction not found"
            )
        ]
    )
    fun delete(
        @Parameter(
            description = "Transaction identifier to delete",
            example = "trn-123"
        ) @PathVariable("trnId") trnId: String
    ): TrnDeleteResponse = trnService.deleteWithSiblings(trnId)

    @GetMapping(
        value = ["/portfolio/{portfolioId}/model/{modelId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get transactions by model for a portfolio",
        description = """
            Retrieves all transactions for a portfolio that belong to a specific rebalance model.

            Use this to:
            * Track positions attributable to a specific investment model
            * Calculate model-level holdings for rebalancing
            * View transaction history for a model
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Model transactions retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun findByModel(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Model identifier",
            example = "model-123"
        ) @PathVariable("modelId") modelId: String
    ): TrnResponse =
        TrnResponse(
            trnFinder.findByPortfolioAndModel(portfolioId, modelId)
        )

    @PostMapping(
        value = ["/move"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(summary = "Move position transactions to another portfolio")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Position moved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio or asset not found"
            )
        ]
    )
    fun movePosition(
        @RequestBody request: PositionMoveRequest
    ): PositionMoveResponse = positionMoveService.movePosition(request)
}