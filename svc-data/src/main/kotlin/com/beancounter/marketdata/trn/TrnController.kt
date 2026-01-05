package com.beancounter.marketdata.trn

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.TrnDeleteResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.DateUtils
import com.opencsv.CSVWriterBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
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
 * MVC controller for Transaction related operations.
 * Provides endpoints for managing financial transactions including trades, dividends, and other corporate actions.
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
    var trnQueryService: TrnQueryService,
    var dateUtils: DateUtils,
    var trnIoDefinition: TrnIoDefinition
) {
    @GetMapping(
        value = ["/portfolio/{portfolioId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
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
            ), ApiResponse(
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
        ) @RequestParam(required = false) asAt: String = dateUtils.today()
    ): TrnResponse =
        TrnResponse(
            trnService.findForPortfolio(
                portfolioId,
                dateUtils.getFormattedDate(asAt)
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
        @Parameter(
            description = "Unique transaction identifier",
            example = "trn-123"
        ) @PathVariable("trnId") trnId: String
    ): TrnResponse =
        TrnResponse(
            trnService.getPortfolioTrn(
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
    ): TrnResponse =
        TrnResponse(
            trnService.save(
                trnRequest.portfolioId,
                trnRequest
            )
        )

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
    ): TrnDeleteResponse = TrnDeleteResponse(trnService.delete(trnId))

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
    ): TrnResponse =
        TrnResponse(
            trnQueryService.findAssetTrades(
                portfolioId,
                assetId
            )
        )

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
                query.portfolio,
                query.assetId,
                query.tradeDate
            )
        )

    @GetMapping(value = ["/portfolio/{portfolioId}/export"])
    @Operation(
        summary = "Export portfolio transactions to CSV",
        description = """
            Exports all transactions for a portfolio to CSV format.
            The file will be downloaded with the portfolio ID as the filename.

            Use this to:
            * Export transaction data for external analysis
            * Create backup files of transaction history
            * Generate reports for compliance or auditing
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "CSV file generated and downloaded successfully",
                content = [
                    Content(
                        mediaType = "text/csv"
                    )
                ]
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun export(
        response: HttpServletResponse,
        @Parameter(
            description = "Portfolio identifier to export",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String
    ) {
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"$portfolioId.csv\""
        )
        val trnResponse =
            trnService.findForPortfolio(
                portfolioId,
                dateUtils.date
            )

        val csvWriter = CSVWriterBuilder(response.writer).withSeparator(',').build()
        csvWriter.writeNext(
            trnIoDefinition.headers(),
            false
        )
        for (datum in trnResponse) {
            csvWriter.writeNext(
                trnIoDefinition.export(datum),
                false
            )
        }
        csvWriter.close()
    }

    @GetMapping(
        value = ["/portfolio/{portfolioId}/status/{status}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get transactions for portfolio by status",
        description = """
            Retrieves all transactions for a portfolio with a specific status.
            Valid statuses: PROPOSED, CONFIRMED, SETTLED

            Use this to:
            * View pending/proposed transactions that need review
            * Filter transactions by their processing status
            * Find unsettled transactions for a portfolio
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transactions retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun findByStatus(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Transaction status to filter by",
            example = "PROPOSED"
        ) @PathVariable("status") status: String
    ): TrnResponse {
        val trnStatus =
            com.beancounter.common.model.TrnStatus
                .valueOf(status.uppercase())
        return TrnResponse(trnService.findByStatus(portfolioId, trnStatus))
    }

    @PostMapping(
        value = ["/portfolio/{portfolioId}/settle"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Settle proposed transactions",
        description = """
            Updates the status of PROPOSED transactions to SETTLED.
            Only transactions with PROPOSED status will be affected.

            Use this to:
            * Finalize proposed transactions after review
            * Convert pending rebalance transactions to actual trades
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transactions settled successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun settleTransactions(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Request body containing transaction IDs to settle"
        ) @RequestBody request: SettleTransactionsRequest
    ): TrnResponse = TrnResponse(trnService.settleTransactions(portfolioId, request.trnIds))
}

data class SettleTransactionsRequest(
    val trnIds: List<String>
)