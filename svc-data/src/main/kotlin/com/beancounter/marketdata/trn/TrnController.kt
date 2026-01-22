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
import java.math.BigDecimal
import java.time.YearMonth

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
        @PathVariable @Parameter(
            description = "Unique transaction identifier",
            example = "trn-123"
        ) trnId: String
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
            Valid statuses: PROPOSED, SETTLED

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

    @GetMapping(
        value = ["/proposed"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all proposed transactions for current user",
        description = """
            Retrieves all PROPOSED transactions across all portfolios owned by the current user.
            This is useful for showing a unified view of all pending transactions that need review.

            Use this to:
            * Display all pending transactions in a notification center
            * Show users what transactions need to be settled
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Proposed transactions retrieved successfully"
            )
        ]
    )
    fun findProposed(): TrnResponse = TrnResponse(trnService.findProposedForUser())

    @GetMapping(
        value = ["/proposed/count"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get count of proposed transactions for current user",
        description = """
            Returns the count of PROPOSED transactions across all portfolios owned by the current user.
            This is useful for displaying a badge or notification indicator.

            Use this to:
            * Display a notification badge count
            * Quickly check if there are pending transactions
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Count retrieved successfully"
            )
        ]
    )
    fun countProposed(): Map<String, Long> = mapOf("count" to trnService.countProposedForUser())

    @GetMapping(
        value = ["/settled"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all settled transactions for current user on a specific date",
        description = """
            Retrieves all SETTLED transactions across all portfolios owned by the current user
            for a specific trade date.

            Use this to:
            * View settled transactions on a specific date
            * Review what was executed on a particular trading day
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Settled transactions retrieved successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "tradeDate parameter is required"
            )
        ]
    )
    fun findSettled(
        @Parameter(
            description = "Trade date in YYYY-MM-DD format",
            example = "2026-01-22",
            required = true
        ) @RequestParam("tradeDate") tradeDate: java.time.LocalDate
    ): TrnResponse = TrnResponse(trnService.findSettledForUser(tradeDate))

    @GetMapping(
        value = ["/investments/monthly"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get monthly investment summary",
        description = """
            Returns the net amount invested (BUY + ADD - SELL) in a specific month.
            Optionally scoped to specific portfolios and converted to a target currency.

            Use this to:
            * Track investment progress against monthly goals
            * Display investment metrics on dashboard
            * Monitor contribution patterns
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Monthly investment summary retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Monthly Investment",
                                value = """
                                {
                                  "yearMonth": "2024-01",
                                  "totalInvested": 5000.00,
                                  "currency": "USD"
                                }
                                """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getMonthlyInvestment(
        @Parameter(
            description = "Year and month in YYYY-MM format. Defaults to current month.",
            example = "2024-01"
        ) @RequestParam(required = false) yearMonth: String?,
        @Parameter(
            description = "Target currency code for FX conversion. Required for accurate results.",
            example = "USD"
        ) @RequestParam(required = false) currency: String?,
        @Parameter(
            description = "Comma-separated portfolio IDs to scope. Empty = all user's portfolios.",
            example = "portfolio-1,portfolio-2"
        ) @RequestParam(required = false) portfolioIds: String?
    ): MonthlyInvestmentResponse {
        val month =
            if (yearMonth != null) {
                YearMonth.parse(yearMonth)
            } else {
                YearMonth.now()
            }

        val portfolioIdList = portfolioIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()

        val total =
            if (currency != null) {
                trnService.getMonthlyInvestmentConverted(month, portfolioIdList, currency)
            } else {
                trnService.getMonthlyInvestment(month)
            }

        return MonthlyInvestmentResponse(
            yearMonth = month.toString(),
            totalInvested = total,
            currency = currency
        )
    }

    @GetMapping(
        value = ["/investments/monthly/transactions"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get monthly investment transactions for current user",
        description = """
            Returns all investment transactions (BUY and ADD) for the current user
            in a specific month. Defaults to the current month if not specified.

            Use this to:
            * View individual investment transactions for the month
            * Provide detailed breakdown of monthly investing activity
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Monthly investment transactions retrieved successfully"
            )
        ]
    )
    fun getMonthlyInvestmentTransactions(
        @Parameter(
            description = "Year and month in YYYY-MM format. Defaults to current month.",
            example = "2024-01"
        ) @RequestParam(required = false) yearMonth: String?
    ): TrnResponse {
        val month =
            if (yearMonth != null) {
                YearMonth.parse(yearMonth)
            } else {
                YearMonth.now()
            }
        return TrnResponse(trnService.getMonthlyInvestmentTransactions(month))
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

    @GetMapping(
        value = ["/{portfolioId}/cash-ladder/{cashAssetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get cash ladder for a specific cash asset",
        description = """
            Retrieves all transactions that impacted a specific cash position (settlement account).
            This is useful for account reconciliation, showing all buys, sells, deposits, withdrawals
            and other transactions that affected the cash balance.

            Use this to:
            * View all transactions settled to a specific cash account
            * Reconcile cash balances with external statements
            * Track cash flow for a specific settlement account
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Cash ladder retrieved successfully"
            ), ApiResponse(
                responseCode = "404",
                description = "Portfolio or cash asset not found"
            )
        ]
    )
    fun getCashLadder(
        @Parameter(
            description = "Portfolio identifier",
            example = "portfolio-123"
        ) @PathVariable("portfolioId") portfolioId: String,
        @Parameter(
            description = "Cash asset identifier",
            example = "cash-usd-123"
        ) @PathVariable("cashAssetId") cashAssetId: String
    ): TrnResponse = TrnResponse(trnService.getCashLadder(portfolioId, cashAssetId))

    @GetMapping(
        value = ["/broker/{brokerId}/holdings"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get holdings by broker for reconciliation",
        description = """
            Calculates holdings (quantities) for all assets transacted through a specific broker.
            This aggregates settled transactions to show net positions per asset.

            Use this to:
            * Reconcile holdings with broker statements
            * Verify quantities match broker records
            * Identify discrepancies between system and broker
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Broker holdings retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Broker not found"
            )
        ]
    )
    fun getBrokerHoldings(
        @Parameter(
            description = "Broker identifier",
            example = "broker-123"
        ) @PathVariable("brokerId") brokerId: String
    ): BrokerHoldingsResponse = trnService.getBrokerHoldings(brokerId)

    @GetMapping(
        value = ["/broker/{brokerId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get transactions by broker for position building",
        description = """
            Retrieves all transactions for a specific broker as of a given date.
            Includes all transaction types (BUY, SELL, SPLIT, DIVI, etc.) needed
            for accurate position calculation with split adjustments.

            Used by svc-position to build holdings with correct quantities.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Broker transactions retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Broker not found"
            )
        ]
    )
    fun getBrokerTransactions(
        @Parameter(
            description = "Broker identifier",
            example = "broker-123"
        ) @PathVariable("brokerId") brokerId: String,
        @Parameter(
            description = "Date to retrieve transactions for (YYYY-MM-DD format)",
            example = "2024-01-15"
        ) @RequestParam(required = false) asAt: String = dateUtils.today()
    ): TrnResponse =
        TrnResponse(
            trnService.findForBroker(
                brokerId,
                dateUtils.getFormattedDate(asAt)
            )
        )
}

data class SettleTransactionsRequest(
    val trnIds: List<String>
)

data class BrokerHoldingTransaction(
    val id: String,
    val portfolioId: String,
    val portfolioCode: String,
    val tradeDate: String,
    val trnType: String,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val tradeAmount: BigDecimal
)

data class BrokerPortfolioGroup(
    val portfolioId: String,
    val portfolioCode: String,
    val quantity: BigDecimal,
    val transactions: List<BrokerHoldingTransaction>
)

data class BrokerHoldingPosition(
    val assetId: String,
    val assetCode: String,
    val assetName: String?,
    val market: String,
    val quantity: BigDecimal,
    val portfolioGroups: List<BrokerPortfolioGroup>
)

data class BrokerHoldingsResponse(
    val brokerId: String,
    val brokerName: String,
    val holdings: List<BrokerHoldingPosition>
)

data class MonthlyInvestmentResponse(
    val yearMonth: String,
    val totalInvested: BigDecimal,
    val currency: String? = null
)