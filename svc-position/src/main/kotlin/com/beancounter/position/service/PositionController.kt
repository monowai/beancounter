package com.beancounter.position.service

import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.FxService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.AllocationResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.SectorExposureResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Valuation
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Position management controller for portfolio positions and valuations.
 * Provides endpoints for retrieving and calculating portfolio positions.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@RestController
@RequestMapping
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Position Management",
    description = "Operations for retrieving and calculating portfolio positions and valuations"
)
class PositionController(
    private val portfolioServiceClient: PortfolioServiceClient,
    private val dateUtils: DateUtils,
    private val allocationService: AllocationService,
    private val sectorExposureService: SectorExposureService,
    private val fxService: FxService
) {
    private lateinit var valuationService: Valuation

    @Autowired
    fun setValuationService(valuationService: Valuation) {
        this.valuationService = valuationService
    }

    @GetMapping(
        value = ["/id/{id}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get positions by portfolio ID",
        description = """
            Retrieves portfolio positions using the portfolio's internal ID.
            Calculates current positions and valuations as of the specified date.
            
            Use this to:
            * Get detailed position information for a portfolio
            * Calculate portfolio valuations at specific dates
            * View asset holdings and their current values
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Positions retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Position Response",
                                value = """
                                {
                                  "data": [
                                    {
                                      "assetId": "AAPL",
                                      "quantity": 100,
                                      "marketValue": 15025.00,
                                      "costBasis": 14000.00,
                                      "unrealizedGainLoss": 1025.00
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
    fun byId(
        @Parameter(
            description = "Portfolio internal identifier",
            example = "portfolio-123"
        )
        @PathVariable id: String,
        @Parameter(
            description = "Valuation date (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(
            value = "asAt",
            required = false
        ) asAt: String = dateUtils.offsetDateString(),
        @Parameter(
            description = "Whether to include market values in the response",
            example = "true"
        )
        @RequestParam(
            value = "value",
            defaultValue = "true"
        ) value: Boolean
    ): PositionResponse {
        val portfolio = portfolioServiceClient.getPortfolioById(id)
        return valuationService.getPositions(
            portfolio,
            asAt,
            value
        )
    }

    @GetMapping(
        value = ["/{code}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get positions by portfolio code",
        description = """
            Retrieves portfolio positions using the portfolio's code.
            Calculates current positions and valuations as of the specified date.
            
            Use this to:
            * Get position information using portfolio codes
            * Calculate valuations for specific dates
            * View portfolio holdings and performance
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Positions retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Portfolio not found"
            )
        ]
    )
    fun get(
        @Parameter(
            description = "Portfolio code",
            example = "MY_PORTFOLIO"
        )
        @PathVariable code: String,
        @Parameter(
            description = "Valuation date (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(
            value = "asAt",
            required = false
        ) asAt: String = DateUtils.TODAY,
        @Parameter(
            description = "Whether to include market values in the response",
            example = "true"
        )
        @RequestParam(
            value = "value",
            defaultValue = "true"
        ) value: Boolean
    ): PositionResponse {
        log.debug("asAt: $asAt")
        val portfolio = portfolioServiceClient.getPortfolioByCode(code)
        return valuationService.getPositions(
            portfolio,
            asAt,
            value
        )
    }

    @PostMapping(
        value = ["/query"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Query positions with custom parameters",
        description = """
            Queries portfolio positions using custom parameters.
            This endpoint provides flexible position querying capabilities.

            Use this to:
            * Query positions with specific criteria
            * Filter positions by asset or date ranges
            * Get customized position reports
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Position query executed successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid query parameters"
            )
        ]
    )
    fun query(
        @Parameter(
            description = "Position query parameters"
        )
        @RequestBody trnQuery: TrustedTrnQuery
    ): PositionResponse = valuationService.build(trnQuery)

    @GetMapping(
        value = ["/aggregated"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get aggregated positions across portfolios",
        description = """
            Retrieves aggregated positions across user portfolios.
            Positions for the same asset from different portfolios are combined.

            Use this to:
            * View total asset allocation across all or selected portfolios
            * Calculate overall portfolio exposure
            * Generate consolidated position reports
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Aggregated positions retrieved successfully"
            )
        ]
    )
    fun aggregated(
        @Parameter(
            description = "Valuation date (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(
            value = "asAt",
            required = false
        ) asAt: String = DateUtils.TODAY,
        @Parameter(
            description = "Whether to include market values in the response",
            example = "true"
        )
        @RequestParam(
            value = "value",
            defaultValue = "true"
        ) value: Boolean,
        @Parameter(
            description = "Comma-separated portfolio codes to include. If empty, all portfolios are included.",
            example = "PORTFOLIO1,PORTFOLIO2"
        )
        @RequestParam(
            value = "codes",
            required = false
        ) codes: String?
    ): PositionResponse {
        val allPortfolios = portfolioServiceClient.portfolios.data
        val selectedPortfolios =
            if (codes.isNullOrBlank()) {
                allPortfolios
            } else {
                val codeSet = codes.split(",").map { it.trim() }.toSet()
                allPortfolios.filter { it.code in codeSet }
            }
        return valuationService.getAggregatedPositions(
            selectedPortfolios,
            asAt,
            value
        )
    }

    @GetMapping(
        value = ["/allocation"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get asset allocation across portfolios",
        description = """
            Calculates asset allocation breakdown by category across selected portfolios.
            Groups positions by asset category (Cash, Equity, ETF, Property, etc.)
            and returns allocation percentages for retirement planning.

            Use this to:
            * Get current asset allocation for retirement planning
            * Determine portfolio balance across asset classes
            * Pre-populate allocation assumptions based on actual holdings

            Optional currency parameter converts totalValue to the target currency
            (useful when plan expenses are in a different currency than portfolio holdings).
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Allocation calculated successfully"
            )
        ]
    )
    fun allocation(
        @Parameter(
            description = "Valuation date (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(
            value = "asAt",
            required = false
        ) asAt: String = DateUtils.TODAY,
        @Parameter(
            description = "Comma-separated portfolio IDs to include. If empty, all portfolios are included.",
            example = "portfolio-id-1,portfolio-id-2"
        )
        @RequestParam(
            value = "ids",
            required = false
        ) ids: String?,
        @Parameter(
            description =
                "Target currency for totalValue conversion (e.g., NZD, USD). " +
                    "If not specified, returns values in portfolio currency.",
            example = "NZD"
        )
        @RequestParam(
            value = "currency",
            required = false
        ) targetCurrency: String?
    ): AllocationResponse {
        val allPortfolios = portfolioServiceClient.portfolios.data
        val selectedPortfolios =
            if (ids.isNullOrBlank()) {
                allPortfolios
            } else {
                val idSet = ids.split(",").map { it.trim() }.toSet()
                allPortfolios.filter { it.id in idSet }
            }

        if (selectedPortfolios.isEmpty()) {
            return AllocationResponse()
        }

        val positions =
            valuationService.getAggregatedPositions(
                selectedPortfolios,
                asAt,
                true
            )

        val allocation = allocationService.calculateAllocation(positions.data)

        // If target currency is specified and different from allocation currency, convert
        if (!targetCurrency.isNullOrBlank() &&
            !targetCurrency.equals(allocation.currency, ignoreCase = true)
        ) {
            return AllocationResponse(
                data = allocationService.convertCurrency(allocation, targetCurrency, fxService, asAt)
            )
        }

        return AllocationResponse(data = allocation)
    }

    @GetMapping(
        value = ["/sector-exposure"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get sector exposure across portfolios",
        description = """
            Calculates sector exposure breakdown across selected portfolios.
            - ETFs: Uses weighted sector allocations from stored exposures
            - Equities: Uses 100% allocation to classified sector
            - Other: Groups by report category as "Unclassified"

            Use this to:
            * View sector allocation for retirement planning
            * Determine portfolio concentration by sector
            * Analyze sector diversification
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Sector exposure calculated successfully"
            )
        ]
    )
    fun sectorExposure(
        @Parameter(
            description = "Valuation date (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @RequestParam(
            value = "asAt",
            required = false
        ) asAt: String = DateUtils.TODAY,
        @Parameter(
            description = "Comma-separated portfolio IDs to include. If empty, all portfolios are included.",
            example = "portfolio-id-1,portfolio-id-2"
        )
        @RequestParam(
            value = "ids",
            required = false
        ) ids: String?
    ): SectorExposureResponse {
        val allPortfolios = portfolioServiceClient.portfolios.data
        val selectedPortfolios =
            if (ids.isNullOrBlank()) {
                allPortfolios
            } else {
                val idSet = ids.split(",").map { it.trim() }.toSet()
                allPortfolios.filter { it.id in idSet }
            }

        if (selectedPortfolios.isEmpty()) {
            return SectorExposureResponse()
        }

        val positions =
            valuationService.getAggregatedPositions(
                selectedPortfolios,
                asAt,
                true
            )

        return SectorExposureResponse(
            data = sectorExposureService.calculateSectorExposure(positions.data)
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}