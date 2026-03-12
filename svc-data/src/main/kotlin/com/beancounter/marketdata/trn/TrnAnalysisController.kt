package com.beancounter.marketdata.trn

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.TrnResponse
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

/**
 * Controller for transaction analysis and reporting endpoints.
 * Provides summary, monthly investment, and income reporting capabilities.
 */
@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Transaction Analysis",
    description =
        "Analysis and reporting operations for transactions " +
            "including summaries, monthly investments, and income"
)
class TrnAnalysisController(
    var trnAnalysisService: TrnAnalysisService
) {
    @GetMapping(
        value = ["/summary"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get transaction summary for rolling window",
        description = """
            Returns total purchases, total sales, and net investment for a rolling N-week window.
            All amounts are converted to the specified target currency.

            Use this to:
            * Track investment activity against monthly targets
            * Monitor actual vs planned investment
            * Display investment metrics on independence dashboards
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Transaction summary retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Transaction Summary",
                                value = """
                                {
                                  "data": {
                                    "totalPurchases": 5000.00,
                                    "totalSales": 1500.00,
                                    "netInvestment": 3500.00,
                                    "periodStart": "2024-12-28",
                                    "periodEnd": "2024-01-25",
                                    "currency": "NZD"
                                  }
                                }
                                """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getTransactionSummary(
        @Parameter(
            description = "Number of weeks to include in the summary. Defaults to 4.",
            example = "4"
        ) @RequestParam(required = false, defaultValue = "4") weeks: Int,
        @Parameter(
            description = "Target currency code for FX conversion. Required.",
            example = "NZD"
        ) @RequestParam(required = true) currency: String
    ): TransactionSummaryResponse =
        TransactionSummaryResponse(
            trnAnalysisService.getTransactionSummary(weeks, currency)
        )

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
                trnAnalysisService.getMonthlyInvestmentConverted(month, portfolioIdList, currency)
            } else {
                trnAnalysisService.getMonthlyInvestment(month)
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
        return TrnResponse(trnAnalysisService.getMonthlyInvestmentTransactions(month))
    }

    @GetMapping(
        value = ["/income/monthly"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get monthly income report",
        description = """
            Returns income (dividends) aggregated by month over a rolling period.
            Can be grouped by asset or category, and optionally scoped to specific portfolios.

            Use this to:
            * View monthly income trends
            * Analyze income by asset class, sector, currency, or market
            * Track dividend income over time
            * Click through to see top 10 contributors per group
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Monthly income report retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Monthly Income",
                                value = """
                                {
                                  "startMonth": "2024-01",
                                  "endMonth": "2024-12",
                                  "totalIncome": 5000.00,
                                  "groupBy": "assetClass",
                                  "months": [
                                    {"yearMonth": "2024-01", "income": 400.00},
                                    {"yearMonth": "2024-02", "income": 450.00}
                                  ],
                                  "groups": [
                                    {
                                      "groupKey": "Equity",
                                      "totalIncome": 1200.00,
                                      "monthlyData": [
                                        {"yearMonth": "2024-01", "income": 100.00}
                                      ],
                                      "topContributors": [
                                        {"assetId": "abc123", "assetCode": "AAPL", "assetName": "Apple Inc.", "totalIncome": 500.00},
                                        {"assetId": "def456", "assetCode": "MSFT", "assetName": "Microsoft Corp", "totalIncome": 400.00}
                                      ]
                                    }
                                  ]
                                }
                                """
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun getMonthlyIncome(
        @Parameter(
            description = "Number of months to include. Defaults to 12.",
            example = "12"
        ) @RequestParam(required = false, defaultValue = "12") months: Int,
        @Parameter(
            description = "End month in YYYY-MM format. Defaults to current month.",
            example = "2024-12"
        ) @RequestParam(required = false) endMonth: String?,
        @Parameter(
            description = "Comma-separated portfolio IDs to scope. Empty = all user's portfolios.",
            example = "portfolio-1,portfolio-2"
        ) @RequestParam(required = false) portfolioIds: String?,
        @Parameter(
            description = "Grouping option: 'assetClass', 'sector', 'currency', or 'market'. Defaults to 'assetClass'.",
            example = "assetClass"
        ) @RequestParam(required = false, defaultValue = "assetClass") groupBy: String
    ): MonthlyIncomeResponse {
        val end =
            if (endMonth != null) {
                YearMonth.parse(endMonth)
            } else {
                YearMonth.now()
            }
        val portfolioIdList = portfolioIds?.split(",")?.filter { it.isNotBlank() } ?: emptyList()
        return trnAnalysisService.getMonthlyIncome(months, end, portfolioIdList, groupBy)
    }
}