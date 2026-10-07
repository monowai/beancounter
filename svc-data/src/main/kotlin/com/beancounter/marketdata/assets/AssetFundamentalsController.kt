package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.AssetFundamentals
import com.beancounter.marketdata.classification.AssetFundamentalsRepository
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Read side of the per-asset fundamentals snapshot written by the SEC classification pass.
 * Split out of [AssetController], which is already long.
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
class AssetFundamentalsController(
    private val fundamentalsRepository: AssetFundamentalsRepository
) {
    @GetMapping("/{assetId}/fundamentals")
    @Operation(
        summary = "Get an asset's latest fiscal-year fundamentals",
        description =
            "EPS, revenue, net income, dividends per share and shares outstanding as last snapshotted from SEC EDGAR."
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "200", description = "Fundamentals found"),
            ApiResponse(responseCode = "404", description = "No fundamentals snapshot for this asset")
        ]
    )
    fun getFundamentals(
        @Parameter(description = "Unique identifier of the asset")
        @PathVariable assetId: String
    ): AssetFundamentals =
        fundamentalsRepository
            .findById(assetId)
            .orElseThrow { NotFoundException("No fundamentals for asset: $assetId") }
}