package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetCategoryResponse
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.assets.search.AssetSearchService
import com.beancounter.marketdata.price.MarketDataService
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Market Data MVC for asset management operations.
 * Provides endpoints for creating, finding, searching and enriching financial assets.
 *
 * @see OwnedAssetController for the caller's own assets
 * @see AssetAdminController for admin changes to any asset
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
    private val assetFinder: AssetFinder,
    private val assetCategoryConfig: AssetCategoryConfig,
    private val assetSearchService: AssetSearchService,
    private val marketDataService: MarketDataService,
    private val marketService: com.beancounter.marketdata.markets.MarketService
) {
    companion object {
        private const val MAX_INPUT_LENGTH = 50

        // Sanitize input to prevent path traversal patterns
        private fun sanitize(input: String?): String? =
            input
                ?.replace(Regex("\\.{2,}[/\\\\]?|[/\\\\]"), "")
                ?.take(MAX_INPUT_LENGTH)
    }

    @GetMapping(
        value = ["/categories"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all asset categories",
        description = "Returns all available asset categories that can be used when creating custom assets"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Categories retrieved successfully",
                content = [Content(schema = Schema(implementation = AssetCategoryResponse::class))]
            )
        ]
    )
    fun getCategories(): AssetCategoryResponse = AssetCategoryResponse(assetCategoryConfig.getCategories().values)

    @GetMapping(
        value = ["/search"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Search for assets by keyword",
        description = """
            Searches for assets matching the given keyword.
            - For PRIVATE market: Searches user-owned assets by code
            - For public markets (US, ASX, etc): Uses AlphaVantage SYMBOL_SEARCH
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Search results returned successfully",
                content = [Content(schema = Schema(implementation = AssetSearchResponse::class))]
            )
        ]
    )
    fun searchAssets(
        @Parameter(description = "Search keyword (asset code or partial code)", example = "AAPL")
        @RequestParam keyword: String,
        @Parameter(description = "Market code to search in (e.g., US, ASX, PRIVATE)", example = "US")
        @RequestParam(required = false) market: String?
    ): AssetSearchResponse =
        assetSearchService.search(
            sanitize(keyword) ?: "",
            sanitize(market)
        )

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
        @PathVariable code: String,
        @Parameter(
            description =
                "When true, the supplied market is run through " +
                    "MarketService.canonical first — colloquial inputs " +
                    "(NASDAQ/NYSE/AMEX/ARCA, NASAQ, DOW, DOW JONES) collapse " +
                    "to the active US market so the lookup hits the same row " +
                    "as a request that already used the canonical code. " +
                    "Used by the chat agent's by-ticker tools where the LLM " +
                    "has no reason to know BC's exchange conventions. " +
                    "Default false preserves existing CSV-import behaviour."
        )
        @RequestParam(
            value = "canonical",
            defaultValue = "false"
        ) canonical: Boolean
    ): AssetResponse {
        val sanitizedMarket = sanitize(market) ?: ""
        val resolvedMarket =
            if (canonical && sanitizedMarket.isNotBlank()) {
                marketService.canonical(sanitizedMarket).code
            } else {
                sanitizedMarket
            }
        return AssetResponse(
            assetService.findOrCreate(
                AssetInput(
                    resolvedMarket,
                    sanitize(code) ?: ""
                )
            )
        )
    }

    @GetMapping("/{assetId}")
    @Operation(
        summary = "Get asset by ID",
        description = """
            Retrieves a specific asset by its unique identifier.
            Optionally includes current price data when includePrice=true.
        """
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
        @PathVariable assetId: String,
        @Parameter(description = "Include current price data in response", example = "true")
        @RequestParam(required = false, defaultValue = "false") includePrice: Boolean
    ): AssetResponse {
        val asset = assetFinder.find(assetId)
        val price =
            if (includePrice) {
                marketDataService.getPriceResponse(assetId).data.firstOrNull()
            } else {
                null
            }
        return AssetResponse(asset, price)
    }

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

    @GetMapping(
        value = ["/market/{market}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "List assets on a given market",
        description = """
            Returns all assets registered on the supplied market code.
            Primary use case is enumerating reference assets such as the
            pre-seeded benchmark indices on the synthetic INDEX market,
            which clients render as a chart-overlay selector.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Assets retrieved successfully",
                content = [Content(schema = Schema(implementation = AssetUpdateResponse::class))]
            )
        ]
    )
    fun getAssetsByMarket(
        @Parameter(description = "Market code to filter by", example = "INDEX")
        @PathVariable market: String
    ): AssetUpdateResponse {
        val assets = assetFinder.findByMarketCode(market.uppercase())
        return AssetUpdateResponse(assets.associateBy { it.id })
    }

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