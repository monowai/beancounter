package com.beancounter.marketdata.classification.refresh

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.BackfillResponse
import com.beancounter.marketdata.classification.enricher.EnrichmentResult
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * REST endpoints that fetch classifications from providers: the bulk backfill and the refreshes.
 */
@RestController
@RequestMapping("/classifications")
@CrossOrigin
@Tag(name = "Classifications", description = "Asset classification and sector exposure management")
class ClassificationRefreshController(
    private val classificationRefreshService: ClassificationRefreshService
) {
    private val log = LoggerFactory.getLogger(ClassificationRefreshController::class.java)

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
        @Parameter(
            description = "Assets to attempt, 1..${ClassificationRefreshService.MAX_BACKFILL_LIMIT}; larger is capped",
            example = "100"
        )
        @RequestParam(required = false, defaultValue = "100") limit: Int = 100
    ): BackfillResponse = BackfillResponse(data = classificationRefreshService.backfill(category, limit))

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
        val result = classificationRefreshService.refreshAsset(assetId)
        return mapOf(
            "assetId" to assetId,
            "refreshed" to (result == EnrichmentResult.ENRICHED),
            "result" to result.name
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
        val result = classificationRefreshService.refreshAssetByCode(market, code)
        return mapOf(
            "market" to market,
            "code" to code,
            "refreshed" to (result == EnrichmentResult.ENRICHED),
            "result" to result.name
        )
    }
}