package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetStatusRequest
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
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Admin changes to any asset, whoever owns it, and the price refresh.
 */
@RestController
@RequestMapping("/assets")
@CrossOrigin
@PreAuthorize(
    AuthConstants.USER_OR_SYSTEM
)
@Tag(
    name = "Assets",
    description = "Asset management operations"
)
class AssetAdminController(
    private val assetAdministration: AssetAdministration,
    private val priceRefresh: PriceRefresh
) {
    @PostMapping(value = ["/price/refresh"])
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(
        summary = "Refresh prices for all assets",
        description = """
            Triggers a price refresh for all assets in the system. Requires admin scope.
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
    ) = assetAdministration.deleteAsset(assetId)

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
    ): AssetResponse = AssetResponse(assetAdministration.updateAsset(assetId, assetInput))

    @PatchMapping(
        value = ["/{assetId}/status"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @Operation(
        summary = "Update asset status",
        description = """
            Updates the status of an asset (Active/Inactive).
            Use this to deactivate delisted assets so they are excluded from price refresh.
            Requires admin scope: the status applies to every user of the asset.
        """
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
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
    ): AssetResponse = AssetResponse(assetAdministration.updateStatus(assetId, request.status))
}