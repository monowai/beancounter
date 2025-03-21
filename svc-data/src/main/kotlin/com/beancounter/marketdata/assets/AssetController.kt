package com.beancounter.marketdata.assets

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.providers.PriceRefresh
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
 * Market Data MVC.
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
class AssetController(
    private val assetService: AssetService,
    private val priceRefresh: PriceRefresh
) {
    @GetMapping(
        value = ["/{market}/{code}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun getAsset(
        @PathVariable market: String,
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

    @GetMapping(value = ["/{assetId}"])
    fun getAsset(
        @PathVariable assetId: String
    ): AssetResponse = AssetResponse(assetService.find(assetId))

    @PostMapping(value = ["/{assetId}/enrich"])
    fun enrichAsset(
        @PathVariable assetId: String
    ): AssetResponse = AssetResponse(assetService.enrich(assetService.find(assetId)))

    @PostMapping(value = ["/price/refresh"])
    fun updatePrices() = priceRefresh.updatePrices()

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun update(
        @RequestBody assetRequest: AssetRequest
    ): AssetUpdateResponse = assetService.handle(assetRequest)
}