package com.beancounter.marketdata.providers

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.OffMarketPriceRequest
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.providers.alpha.AlphaEventService
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
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Market Data MVC for price management operations.
 * Provides endpoints for retrieving and managing market prices for financial assets.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/prices")
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Price Management",
    description = "Operations for retrieving and managing market prices for financial assets"
)
class PriceController(
    private val marketDataService: MarketDataService,
    private val priceRefresh: PriceRefresh,
    private val eventService: AlphaEventService,
    private val priceService: PriceService,
    private val dateUtils: DateUtils
) {
    /**
     * Market:Asset i.e. NYSE:MSFT.
     *
     * @param marketCode BC Market Code or alias
     * @param assetCode  BC Asset Code
     * @return Market Data information for the supplied asset
     */
    @GetMapping(value = ["/{marketCode}/{assetCode}"])
    @Operation(
        summary = "Get price by market and asset code",
        description = """
            Retrieves current market price information for an asset using market and asset codes.
            Format: Market:Asset (e.g., NYSE:MSFT)
            
            Use this to:
            * Get current prices for assets by market and ticker
            * Retrieve price data for display or calculations
            * Access real-time market information
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Price data retrieved successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Price Response",
                                value = """
                                {
                                  "data": [
                                    {
                                      "assetId": "MSFT",
                                      "closePrice": 150.25,
                                      "date": "2024-01-15",
                                      "volume": 25000000
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
                description = "Asset or price data not found"
            )
        ]
    )
    fun getPrice(
        @Parameter(
            description = "Market code (e.g., NYSE, NASDAQ, ASX)",
            example = "NYSE"
        )
        @PathVariable("marketCode") marketCode: String,
        @Parameter(
            description = "Asset ticker code",
            example = "MSFT"
        )
        @PathVariable("assetCode") assetCode: String
    ): PriceResponse = marketDataService.getPriceResponse(marketCode, assetCode)

    @GetMapping(value = ["/{assetId}"])
    @Operation(
        summary = "Get price by asset ID",
        description = """
            Retrieves current market price information for an asset using its internal ID.
            
            Use this to:
            * Get prices for assets using their internal identifier
            * Retrieve price data for known assets
            * Access market information for portfolio calculations
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Price data retrieved successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset or price data not found"
            )
        ]
    )
    fun getPrice(
        @Parameter(
            description = "Internal asset identifier",
            example = "asset-123"
        )
        @PathVariable("assetId") id: String
    ): PriceResponse = marketDataService.getPriceResponse(id)

    /**
     * Market:Asset i.e. NYSE:MSFT.
     *
     * @param assetId Internal BC Asset identifier
     * @return Market Data information for the requested asset
     */
    @GetMapping(value = ["/{assetId}/events"])
    @Operation(
        summary = "Get corporate events for asset",
        description = """
            Retrieves corporate events (dividends, splits, etc.) for a specific asset.
            
            Use this to:
            * View dividend history and corporate actions
            * Track event impact on asset prices
            * Analyze corporate event patterns
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
                description = "Asset not found"
            )
        ]
    )
    fun getEvents(
        @Parameter(
            description = "Internal asset identifier",
            example = "asset-123"
        )
        @PathVariable("assetId") assetId: String
    ): PriceResponse = eventService.getEvents(assetId)

    @PostMapping("/write")
    @Operation(
        summary = "Write off-market price",
        description = """
            Writes a custom price for an asset (off-market pricing).
            This is useful for private assets or custom valuations.
            
            Use this to:
            * Set custom prices for private assets
            * Override market prices with custom valuations
            * Add pricing for assets not traded on public markets
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Off-market price written successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid price data"
            )
        ]
    )
    fun writeOffMarketPrice(
        @Parameter(
            description = "Off-market price request containing asset and price data"
        )
        @RequestBody offMarketPriceRequest: OffMarketPriceRequest
    ): PriceResponse =
        PriceResponse(
            listOf(
                priceService
                    .getMarketData(
                        assetId = offMarketPriceRequest.assetId,
                        date = dateUtils.getFormattedDate(offMarketPriceRequest.date),
                        closePrice = offMarketPriceRequest.closePrice
                    ).get()
            )
        )

    @PostMapping
    @Operation(
        summary = "Get prices for multiple assets",
        description = """
            Retrieves prices for multiple assets in a single request.
            This endpoint handles bulk price retrieval efficiently.
            
            Use this to:
            * Get prices for multiple assets at once
            * Retrieve portfolio price data
            * Batch price requests for performance
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Prices retrieved successfully"
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid price request"
            )
        ]
    )
    fun getPrices(
        @Parameter(
            description = "Price request containing multiple assets"
        )
        @RequestBody priceRequest: PriceRequest
    ): PriceResponse = marketDataService.getAssetPrices(priceRequest)

    @GetMapping("/refresh/{assetId}/{date}")
    @Operation(
        summary = "Refresh price for specific asset and date",
        description = """
            Refreshes the price for a specific asset and date.
            This triggers a price update from external market data sources.
            
            Use this to:
            * Force price updates for specific assets
            * Refresh stale price data
            * Update prices for historical dates
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Price refreshed successfully"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found"
            )
        ]
    )
    fun refreshPrices(
        @Parameter(
            description = "Asset identifier to refresh",
            example = "asset-123"
        )
        @PathVariable assetId: String,
        @Parameter(
            description = "Date to refresh (YYYY-MM-DD format, defaults to today)",
            example = "2024-01-15"
        )
        @PathVariable(required = false) date: String = TODAY
    ): PriceResponse =
        priceRefresh.refreshPrice(
            assetId,
            date
        )

    @PostMapping(
        value = ["/{assetId}/events"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Backfill events for asset",
        description = """
            Initiates a backfill of corporate events for a specific asset.
            This is an asynchronous operation that processes historical events.
            
            Use this to:
            * Populate missing corporate event data
            * Refresh event history for an asset
            * Ensure complete event data coverage
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "202",
                description = "Backfill operation accepted and started"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found"
            )
        ]
    )
    fun backFill(
        @Parameter(
            description = "Asset identifier for event backfill",
            example = "asset-123"
        )
        @PathVariable assetId: String
    ) = marketDataService.backFill(assetId)
}