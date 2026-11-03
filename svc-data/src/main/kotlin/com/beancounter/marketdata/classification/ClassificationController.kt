package com.beancounter.marketdata.classification

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetClassificationsResponse
import com.beancounter.common.contracts.AssetExposuresResponse
import com.beancounter.common.contracts.AssetHoldingsResponse
import com.beancounter.common.contracts.BulkClassificationRequest
import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.DeleteSectorResponse
import com.beancounter.common.contracts.DeleteSectorResult
import com.beancounter.common.contracts.ManualClassificationRequest
import com.beancounter.common.contracts.ManualClassificationResponse
import com.beancounter.common.contracts.SectorInfo
import com.beancounter.common.contracts.SectorsResponse
import com.beancounter.marketdata.classification.refresh.ClassificationRefreshController
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * REST endpoints for reading and setting asset classifications, and for managing sectors.
 * Provider refresh and backfill live in [ClassificationRefreshController].
 */
@RestController
@RequestMapping("/classifications")
@CrossOrigin
@Tag(name = "Classifications", description = "Asset classification and sector exposure management")
class ClassificationController(
    private val classificationService: ClassificationService,
    private val taxonomy: ClassificationTaxonomy,
    private val fundLookThrough: FundLookThrough
) {
    private val log = LoggerFactory.getLogger(ClassificationController::class.java)

    @GetMapping(
        value = ["/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
    )
    @Operation(
        summary = "Get classifications for an asset",
        description = "Returns sector and industry classifications for the specified asset"
    )
    fun getClassifications(
        @PathVariable assetId: String
    ): AssetClassificationsResponse =
        AssetClassificationsResponse(data = classificationService.getClassifications(assetId))

    @GetMapping(
        value = ["/{assetId}/exposures"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
    )
    @Operation(
        summary = "Get sector exposures for an ETF",
        description = "Returns weighted sector exposures for the specified ETF"
    )
    fun getExposures(
        @PathVariable assetId: String
    ): AssetExposuresResponse = AssetExposuresResponse(data = fundLookThrough.getExposures(assetId))

    @GetMapping(
        value = ["/{assetId}/holdings"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
    )
    @Operation(
        summary = "Get top holdings for an ETF",
        description = "Returns the top 10 holdings within the specified ETF"
    )
    fun getHoldings(
        @PathVariable assetId: String
    ): AssetHoldingsResponse = AssetHoldingsResponse(data = fundLookThrough.getHoldings(assetId))

    // A pure read. The widget scope is allowed because svc-position forwards the caller's JWT
    // here to value net worth.
    @PostMapping(
        value = ["/bulk"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(AuthConstants.USER_WIDGET_OR_SYSTEM)
    @Operation(
        summary = "Get classifications for multiple assets",
        description = "Returns sector and industry classifications for multiple assets in bulk"
    )
    fun getBulkClassifications(
        @RequestBody request: BulkClassificationRequest
    ): BulkClassificationResponse =
        BulkClassificationResponse(data = classificationService.getClassificationSummaries(request.assetIds))

    @PutMapping(
        value = ["/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_ADMIN}')"
    )
    @Operation(
        summary = "Manually set sector for an asset",
        description = "Sets a custom sector (e.g., 'Income', 'Diversified', 'Index') for an asset"
    )
    fun setManualClassification(
        @PathVariable assetId: String,
        @RequestBody request: ManualClassificationRequest
    ): ManualClassificationResponse {
        log.info("Setting manual classification for $assetId: sector=${request.sector}")
        val summary = classificationService.setManualClassification(assetId, request.sector, request.industry)
        return ManualClassificationResponse(data = summary)
    }

    @GetMapping(
        value = ["/sectors"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_ADMIN}')"
    )
    @Operation(
        summary = "List all sectors",
        description = "Returns all distinct sectors in the system for use in classification UI"
    )
    fun listSectors(): SectorsResponse {
        val sectors =
            taxonomy.getAllSectors().map { item ->
                SectorInfo(
                    code = item.code,
                    name = item.name,
                    standard = item.standard.key
                )
            }
        return SectorsResponse(data = sectors)
    }

    @DeleteMapping(
        value = ["/sectors/{sectorCode}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_ADMIN}')"
    )
    @Operation(
        summary = "Delete a custom sector",
        description = "Deletes a user-defined sector and removes classifications from all assets using it"
    )
    fun deleteSector(
        @PathVariable sectorCode: String
    ): DeleteSectorResponse {
        log.info("Deleting custom sector: $sectorCode")
        val affectedAssets = taxonomy.deleteSector(sectorCode)
        return DeleteSectorResponse(
            data =
                DeleteSectorResult(
                    sectorCode = sectorCode,
                    affectedAssets = affectedAssets
                )
        )
    }
}