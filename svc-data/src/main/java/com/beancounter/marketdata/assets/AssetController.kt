package com.beancounter.marketdata.assets

import com.beancounter.auth.server.RoleHelper
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.marketdata.service.MarketDataService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/assets")
@CrossOrigin
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
class AssetController @Autowired internal constructor(private val assetService: AssetService, private val marketDataService: MarketDataService) {
    @GetMapping(value = ["/{market}/{code}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getAsset(@PathVariable market: String, @PathVariable code: String): AssetResponse {
        return AssetResponse(assetService.find(market, code))
    }

    @GetMapping(value = ["/{assetId}"])
    fun getAsset(@PathVariable assetId: String): AssetResponse {
        return AssetResponse(assetService.find(assetId))
    }

    @PostMapping(value = ["/{assetId}/enrich"])
    fun enrichAsset(@PathVariable assetId: String): AssetResponse {
        return AssetResponse(assetService.enrich(assetId))
    }

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE])
    fun update(@RequestBody assetRequest: AssetRequest): AssetUpdateResponse {
        return assetService.process(assetRequest)
    }

    @PostMapping(value = ["/{assetId}/events"], produces = [MediaType.APPLICATION_JSON_VALUE])
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun backFill(@PathVariable assetId: String) {
        marketDataService.backFill(assetService.find(assetId))
    }

}