package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.providers.PriceRefresh
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Market Data MVC for asset management operations.
 * Provides endpoints for creating, finding, and managing financial assets.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/assets")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
@Tag(
    name = "Assets",
    description = "Asset management operations"
)
class AssetController(
    private val assetService: AssetService,
    private val priceRefresh: PriceRefresh,
    private val assetFinder: AssetFinder
) {
    @GetMapping(
        value = ["/{market}/{code}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get or create asset by market and code",
        description = """
            Retrieves an existing asset or creates a new one if it doesn't exist.
            This endpoint automatically handles asset creation with market data enrichment.

            Use this to:
            * Look up existing assets by market and ticker code
            * Create new assets that will be automatically enriched with market data
            * Ensure consistent asset representation across the system
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Asset retrieved or created successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Stock Asset",
                                value = """
                                {
                                  "data": {
                                    "id": "asset-123",
                                    "code": "AAPL",
                                    "market": {
                                      "code": "NASDAQ",
                                      "name": "NASDAQ Stock Market"
                                    },
                                    "name": "Apple Inc.",
                                    "category": "EQUITY"
                                  }
                                }
                                """
                            )
                        ]
                    )
                ]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found and could not be created"
            )
        ]
    )
    fun getAsset(
        @Parameter(
            description = "Market code (e.g., NASDAQ, NYSE, ASX)",
            example = "NASDAQ"
        )
        @PathVariable market: String,
        @Parameter(
            description = "Asset ticker code",
            example = "AAPL"
        )
        @PathVariable code: String
    ): AssetResponse =
        AssetResponse(
            assetService.findOrCreate(
                AssetInput(
                    market,
                    code
                )
            )
        )

    @GetMapping("/{assetId}")
    @Operation(
        summary = "Get asset by ID",
        description = "Retrieves a specific asset by its unique identifier"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Asset found successfully",
                content = [Content(schema = Schema(implementation = AssetResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found"
            )
        ]
    )
    fun getAsset(
        @Parameter(description = "Unique identifier of the asset", example = "US:AAPL")
        @PathVariable assetId: String
    ): AssetResponse = AssetResponse(assetFinder.find(assetId))

    @PostMapping("/{assetId}/enrich")
    @Operation(
        summary = "Enrich asset data",
        description = "Enriches an existing asset with additional market data and information"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Asset enriched successfully",
                content = [Content(schema = Schema(implementation = AssetResponse::class))]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Asset not found"
            )
        ]
    )
    fun enrichAsset(
        @Parameter(description = "Unique identifier of the asset to enrich", example = "US:AAPL")
        @PathVariable assetId: String
    ): AssetResponse = AssetResponse(assetService.enrich(assetFinder.find(assetId)))

    @PostMapping(value = ["/price/refresh"])
    @Operation(
        summary = "Refresh prices for all assets",
        description = """
            Triggers a price refresh for all assets in the system.
            This is a background operation that updates market prices.

            Use this to:
            * Update all asset prices with latest market data
            * Ensure price data is current for portfolio calculations
            * Trigger bulk price updates across the system
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Price refresh initiated successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Price Refresh Result",
                                value = "42"
                            )
                        ]
                    )
                ]
            )
        ]
    )
    fun updatePrices() = priceRefresh.updatePrices()

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Create or update multiple assets",
        description = """
            Creates or updates multiple assets in a single request.
            This endpoint handles bulk asset operations efficiently.

            Use this to:
            * Create multiple assets in one transaction
            * Update existing assets with new information
            * Bulk import assets from external systems
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Assets created or updated successfully",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "Bulk Asset Response",
                                value = """
                                {
                                  "data": {
                                    "asset1": {
                                      "id": "asset-123",
                                      "code": "AAPL",
                                      "name": "Apple Inc."
                                    },
                                    "asset2": {
                                      "id": "asset-456",
                                      "code": "MSFT",
                                      "name": "Microsoft Corporation"
                                    }
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
    fun update(
        @Parameter(
            description = "Request containing multiple assets to create or update"
        )
        @RequestBody assetRequest: AssetRequest
    ): AssetUpdateResponse = assetService.handle(assetRequest)
}