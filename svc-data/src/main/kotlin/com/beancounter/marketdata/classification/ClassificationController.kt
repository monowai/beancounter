package com.beancounter.marketdata.classification

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetClassificationsResponse
import com.beancounter.common.contracts.AssetExposuresResponse
import com.beancounter.common.contracts.AssetHoldingsResponse
import com.beancounter.common.contracts.BackfillResponse
import com.beancounter.common.contracts.BackfillResult
import com.beancounter.common.contracts.BulkClassificationRequest
import com.beancounter.common.contracts.BulkClassificationResponse
import com.beancounter.common.contracts.DeleteSectorResponse
import com.beancounter.common.contracts.DeleteSectorResult
import com.beancounter.common.contracts.ManualClassificationRequest
import com.beancounter.common.contracts.ManualClassificationResponse
import com.beancounter.common.contracts.SectorInfo
import com.beancounter.common.contracts.SectorsResponse
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetRepository
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
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST endpoints for asset classification and sector management.
 */
@RestController
@RequestMapping("/classifications")
@CrossOrigin
@Tag(name = "Classifications", description = "Asset classification and sector exposure management")
class ClassificationController(
    private val classificationEnricher: AlphaClassificationEnricher,
    private val classificationService: ClassificationService,
    private val classificationRefreshService: ClassificationRefreshService,
    private val assetRepository: AssetRepository,
    private val assetFinder: AssetFinder
) {
    private val log = LoggerFactory.getLogger(ClassificationController::class.java)

    @PostMapping(
        value = ["/backfill"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(
        summary = "Backfill classifications for existing assets",
        description = "Enriches existing assets with sector/industry classifications from AlphaVantage"
    )
    fun backfillClassifications(
        @RequestParam(required = false) category: String? = null,
        @RequestParam(required = false, defaultValue = "100") limit: Int = 100
    ): BackfillResponse {
        log.info(
            "Starting classification backfill: category=" +
                "$category, limit=$limit"
        )

        val assets =
            assetRepository
                .findAll()
                .asSequence()
                .filter { asset ->
                    category == null || asset.category.equals(category, ignoreCase = true)
                }.filter { asset ->
                    classificationEnricher.canEnrich(asset)
                }.filter { asset ->
                    // Only enrich assets that don't have classifications yet
                    if (classificationEnricher.isEtf(asset)) {
                        !classificationService.hasExposures(asset.id)
                    } else {
                        !classificationService.hasClassifications(asset.id)
                    }
                }.take(limit)
                .toList()

        var processed = 0
        var errors = 0

        for (asset in assets) {
            try {
                val hydratedAsset = assetFinder.hydrateAsset(asset)
                if (classificationEnricher.enrichClassification(hydratedAsset)) {
                    processed++
                }
            } catch (
                @Suppress("TooGenericExceptionCaught")
                e: Exception
            ) {
                // Continue processing other assets even if one fails
                errors++
                log.warn("Failed to enrich classification for ${asset.id}: ${e.message}")
            }
        }

        val result = BackfillResult(processed = processed, errors = errors, total = assets.size)
        log.info("Classification backfill complete: $result")

        return BackfillResponse(data = result)
    }

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
    ): AssetExposuresResponse = AssetExposuresResponse(data = classificationService.getExposures(assetId))

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
    ): AssetHoldingsResponse = AssetHoldingsResponse(data = classificationService.getHoldings(assetId))

    @PostMapping(
        value = ["/bulk"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_SYSTEM}')"
    )
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
            classificationService.getAllSectors().map { item ->
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
        val affectedAssets = classificationService.deleteSector(sectorCode)
        return DeleteSectorResponse(
            data =
                DeleteSectorResult(
                    sectorCode = sectorCode,
                    affectedAssets = affectedAssets
                )
        )
    }

    @PostMapping(
        value = ["/refresh/etf"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(
        summary = "Refresh all ETF sector exposures",
        description = "Re-fetches sector weightings from AlphaVantage for all ETF assets"
    )
    fun refreshEtfSectors(): BackfillResponse {
        log.info("Manual ETF sector refresh triggered")
        val result = classificationRefreshService.refreshEtfSectors()
        return BackfillResponse(data = result)
    }

    @PostMapping(
        value = ["/refresh/equity"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(
        summary = "Refresh all Equity classifications",
        description = "Re-fetches sector/industry classifications from AlphaVantage for all Equity assets"
    )
    fun refreshEquityClassifications(): BackfillResponse {
        log.info("Manual Equity classification refresh triggered")
        val result = classificationRefreshService.refreshEquityClassifications()
        return BackfillResponse(data = result)
    }

    @PostMapping(
        value = ["/refresh/{assetId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_ADMIN}')"
    )
    @Operation(
        summary = "Refresh classification for a single asset",
        description = "Re-fetches classification data from AlphaVantage for the specified asset"
    )
    fun refreshAsset(
        @PathVariable assetId: String
    ): Map<String, Any> {
        log.info("Refreshing classification for asset: $assetId")
        val success = classificationRefreshService.refreshAsset(assetId)
        return mapOf(
            "assetId" to assetId,
            "refreshed" to success
        )
    }

    @PostMapping(
        value = ["/refresh/{market}/{code}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize(
        "hasAnyAuthority('${AuthConstants.SCOPE_USER}', '${AuthConstants.SCOPE_ADMIN}')"
    )
    @Operation(
        summary = "Refresh classification for an asset by market and code",
        description = "Re-fetches classification data from AlphaVantage for the specified asset"
    )
    fun refreshAssetByCode(
        @PathVariable market: String,
        @PathVariable code: String
    ): Map<String, Any> {
        log.info("Refreshing classification for $market:$code")
        val success = classificationRefreshService.refreshAssetByCode(market, code)
        return mapOf(
            "market" to market,
            "code" to code,
            "refreshed" to success
        )
    }

    @GetMapping(
        value = ["/debug/count"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(summary = "Debug: Count all classifications")
    fun debugCount(): Map<String, Long> =
        mapOf(
            "classifications" to classificationService.countClassifications(),
            "exposures" to classificationService.countExposures()
        )

    @GetMapping(
        value = ["/debug/sample"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(summary = "Debug: Get sample classifications")
    fun debugSample(): List<Map<String, Any?>> = classificationService.getSampleClassifications()

    @GetMapping(
        value = ["/debug/sample-exposures"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @PreAuthorize("hasAuthority('${AuthConstants.SCOPE_ADMIN}')")
    @Operation(summary = "Debug: Get sample exposures")
    fun debugSampleExposures(): List<Map<String, Any?>> = classificationService.getSampleExposures()
}