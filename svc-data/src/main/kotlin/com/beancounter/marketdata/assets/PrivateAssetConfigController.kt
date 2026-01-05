package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * REST controller for managing private asset configurations.
 *
 * Provides endpoints for configuring income, expenses, and transaction generation
 * settings for user-owned assets (Real Estate, etc.).
 */
@RestController
@RequestMapping("/assets/config")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
)
@Tag(
    name = "Private Asset Config",
    description = "Configure income and expense settings for private assets"
)
class PrivateAssetConfigController(
    private val configService: PrivateAssetConfigService
) {
    @GetMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    @Operation(
        summary = "Get all configs for current user's assets",
        description = "Returns income/expense configurations for all private assets owned by the authenticated user"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Configs retrieved successfully")
        ]
    )
    fun getMyConfigs(): PrivateAssetConfigsResponse = configService.getMyConfigs()

    @GetMapping(
        value = ["/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Get config for a specific asset",
        description = "Returns income/expense configuration for a specific asset owned by the user"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Config retrieved successfully"),
            ApiResponse(responseCode = "404", description = "Asset not found or not owned by user")
        ]
    )
    fun getConfig(
        @Parameter(description = "Asset ID", example = "abc123")
        @PathVariable assetId: String
    ): PrivateAssetConfigResponse? = configService.getConfig(assetId)

    @PostMapping(
        value = ["/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Save config for an asset",
        description = """
            Creates or updates income/expense configuration for a private asset.
            Settings include:
            - Monthly rental income and currency
            - Management fees (fixed or percentage)
            - Primary residence flag (disables rental income)
            - Liquidation priority for retirement planning
            - Transaction generation settings (day of month, credit account)
        """
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "201", description = "Config saved successfully"),
            ApiResponse(responseCode = "404", description = "Asset not found or not owned by user")
        ]
    )
    fun saveConfig(
        @Parameter(description = "Asset ID", example = "abc123")
        @PathVariable assetId: String,
        @RequestBody request: PrivateAssetConfigRequest
    ): PrivateAssetConfigResponse = configService.saveConfig(assetId, request)

    @DeleteMapping(value = ["/{assetId}"])
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
        summary = "Delete config for an asset",
        description = "Removes income/expense configuration for a private asset"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "204", description = "Config deleted successfully"),
            ApiResponse(responseCode = "404", description = "Config not found or asset not owned by user")
        ]
    )
    fun deleteConfig(
        @Parameter(description = "Asset ID", example = "abc123")
        @PathVariable assetId: String
    ) = configService.deleteConfig(assetId)
}