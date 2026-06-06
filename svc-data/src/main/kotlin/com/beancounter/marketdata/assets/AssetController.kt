package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetCategoryResponse
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetSearchResponse
import com.beancounter.common.contracts.AssetStatusRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.PriceRefresh
import com.opencsv.CSVParserBuilder
import com.opencsv.CSVReaderBuilder
import com.opencsv.CSVWriterBuilder
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
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
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

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
    private val ownedAssetService: OwnedAssetService,
    private val priceRefresh: PriceRefresh,
    private val assetFinder: AssetFinder,
    private val assetIoDefinition: AssetIoDefinition,
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

    @GetMapping(
        value = ["/me"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get all assets owned by the current user",
        description = """
            Retrieves all assets owned by the authenticated user.
            Use this to find user-specific custom assets like bank accounts, real estate, etc.
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
    fun getAllMyAssets(): AssetUpdateResponse = ownedAssetService.findByOwner()

    @GetMapping(
        value = ["/me/{category}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get assets owned by the current user filtered by category",
        description = """
            Retrieves all assets owned by the authenticated user with the specified category.
            Use this to find user-specific assets like bank accounts (ACCOUNT category).
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
    fun getMyAssets(
        @Parameter(description = "Asset category to filter by", example = "ACCOUNT")
        @PathVariable category: String
    ): AssetUpdateResponse = ownedAssetService.findByOwnerAndCategory(category)

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

    @DeleteMapping(
        value = ["/me/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Delete a user-owned asset",
        description = """
            Deletes an asset owned by the authenticated user.
            Only assets with a systemUser matching the current user can be deleted.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Asset deleted successfully"),
            ApiResponse(responseCode = "403", description = "Asset not owned by current user"),
            ApiResponse(responseCode = "404", description = "Asset not found")
        ]
    )
    fun deleteMyAsset(
        @Parameter(description = "Asset ID to delete", example = "abc123")
        @PathVariable assetId: String
    ) = ownedAssetService.deleteOwnedAsset(assetId)

    @PatchMapping(
        value = ["/me/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Update a user-owned asset",
        description = """
            Updates an asset owned by the authenticated user.
            Only assets with a systemUser matching the current user can be updated.
            Allows updating name and currency.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Asset updated successfully",
                content = [Content(schema = Schema(implementation = AssetResponse::class))]
            ),
            ApiResponse(responseCode = "403", description = "Asset not owned by current user"),
            ApiResponse(responseCode = "404", description = "Asset not found")
        ]
    )
    fun updateMyAsset(
        @Parameter(description = "Asset ID to update", example = "abc123")
        @PathVariable assetId: String,
        @RequestBody assetInput: AssetInput
    ): AssetResponse = AssetResponse(ownedAssetService.updateOwnedAsset(assetId, assetInput))

    @DeleteMapping(value = ["/admin/{assetId}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(
        summary = "Admin: delete any asset that is not held in any transaction",
        description = """
            Admin-only deletion of an arbitrary asset (public-market or
            user-owned). The asset must not be referenced by any transaction
            (neither as the trade asset nor as a cash settlement asset). Use
            for reseeding a stale market asset whose code or market mapping
            has been corrected upstream and needs re-resolution via the
            normal lookup flow.

            Cascades the remaining dependents (market data, classifications,
            exposures, holdings, broker settlement accounts, private asset
            config) before deleting the asset itself.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Asset deleted successfully"),
            ApiResponse(
                responseCode = "400",
                description = "Asset is referenced by one or more transactions"
            ),
            ApiResponse(responseCode = "403", description = "Caller lacks the admin role"),
            ApiResponse(responseCode = "404", description = "Asset not found")
        ]
    )
    fun deleteAnyAsset(
        @Parameter(description = "Asset ID to delete", example = "abc123")
        @PathVariable assetId: String
    ) = assetService.deleteAsset(assetId)

    @PatchMapping(
        value = ["/admin/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(
        summary = "Admin: update any asset's category and/or name",
        description = """
            Admin-only escalation of PATCH /assets/me/{assetId} that bypasses
            ownership and allows editing the classification (category) and
            display name of ANY asset — including public-market assets such as
            a Mutual Fund whose category needs correcting to ETF.

            Currency is NOT editable here on purpose: changing a public asset's
            currency would silently re-denominate every user's holdings of it.
            Use a dedicated migration path for that case.

            Sector classification lives on a separate endpoint
            (PUT /classifications/{assetId}) and is unaffected by this call.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Asset updated successfully",
                content = [Content(schema = Schema(implementation = AssetResponse::class))]
            ),
            ApiResponse(responseCode = "403", description = "Caller lacks the admin role"),
            ApiResponse(responseCode = "404", description = "Asset not found")
        ]
    )
    fun updateAnyAsset(
        @Parameter(description = "Asset ID to update", example = "abc123")
        @PathVariable assetId: String,
        @RequestBody assetInput: AssetInput
    ): AssetResponse = AssetResponse(assetService.updateAsset(assetId, assetInput))

    @GetMapping(
        value = ["/me/export"],
        produces = [MediaType.TEXT_PLAIN_VALUE]
    )
    @Operation(
        summary = "Export user-owned assets to CSV",
        description = """
            Exports all assets owned by the authenticated user as a CSV file.
            Use this to backup or transfer your custom assets (accounts, real estate, etc.).
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "CSV file generated successfully"
            )
        ]
    )
    fun exportAssets(response: HttpServletResponse) {
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"assets.csv\""
        )
        val assets = ownedAssetService.findByOwner().data.values

        val csvWriter =
            CSVWriterBuilder(response.writer)
                .withSeparator(',')
                .build()
        csvWriter.writeNext(assetIoDefinition.headers(), false)
        for (asset in assets) {
            csvWriter.writeNext(assetIoDefinition.export(asset), false)
        }
        csvWriter.close()
    }

    @PostMapping(
        value = ["/me/import"],
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Import assets from CSV file",
        description = """
            Imports assets from a CSV file for the authenticated user.
            The CSV should have columns: Code, Name, Category, Currency.
            Existing assets with the same code will be updated.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Assets imported successfully",
                content = [Content(schema = Schema(implementation = AssetUpdateResponse::class))]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid CSV format"
            )
        ]
    )
    fun importAssets(
        @Parameter(description = "CSV file containing assets to import")
        @RequestParam("file") file: MultipartFile
    ): AssetUpdateResponse {
        val owner = ownedAssetService.getCurrentOwnerId()
        val assetInputs = mutableMapOf<String, AssetInput>()

        val csvParser = CSVParserBuilder().withSeparator(',').build()
        val csvReader =
            CSVReaderBuilder(file.inputStream.bufferedReader())
                .withCSVParser(csvParser)
                .build()

        csvReader.use { reader ->
            val lines = reader.readAll()
            if (lines.isEmpty()) {
                return AssetUpdateResponse(emptyMap())
            }

            val startIndex = if (isHeaderRow(lines[0])) 1 else 0
            for (i in startIndex until lines.size) {
                val row = lines[i]
                if (row.isNotEmpty() && row[0].isNotBlank()) {
                    val assetInput = assetIoDefinition.parse(row, owner)
                    assetInputs[assetInput.code] = assetInput
                }
            }
        }

        return assetService.handle(AssetRequest(assetInputs))
    }

    private fun isHeaderRow(row: Array<String>): Boolean =
        row.isNotEmpty() &&
            row[0].equals(AssetIoDefinition.Columns.Code.name, ignoreCase = true)

    @PatchMapping(
        value = ["/{assetId}/status"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Update asset status",
        description = """
            Updates the status of an asset (Active/Inactive).
            Use this to deactivate delisted assets so they are excluded from price refresh.
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Asset status updated successfully",
                content = [Content(schema = Schema(implementation = AssetResponse::class))]
            ),
            ApiResponse(responseCode = "404", description = "Asset not found")
        ]
    )
    fun updateAssetStatus(
        @Parameter(description = "Asset ID to update", example = "abc123")
        @PathVariable assetId: String,
        @RequestBody request: AssetStatusRequest
    ): AssetResponse = AssetResponse(assetService.updateStatus(assetId, request.status))
}