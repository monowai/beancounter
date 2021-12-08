package com.beancounter.marketdata.providers

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.marketdata.assets.AssetService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
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
@RequestMapping("/prices")
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class MarketDataController @Autowired internal constructor(
    private val marketDataService: MarketDataService,
    private val assetService: AssetService,
    private val priceRefresh: PriceRefresh
) {

    /**
     * Market:Asset i.e. NYSE:MSFT.
     *
     * @param marketCode BC Market Code or alias
     * @param assetCode  BC Asset Code
     * @return Market Data information for the supplied asset
     */
    @GetMapping(value = ["/{marketCode}/{assetCode}"])
    fun getPrice(
        @PathVariable("marketCode") marketCode: String,
        @PathVariable("assetCode") assetCode: String
    ): PriceResponse {
        val asset = assetService.findLocally(marketCode, assetCode)
            ?: throw BusinessException(String.format("Asset not found %s/%s", marketCode, assetCode))
        return marketDataService.getPriceResponse(PriceRequest.of(AssetInput(asset)))
    }

    @PostMapping
    fun prices(@RequestBody priceRequest: PriceRequest): PriceResponse {
        log.debug("priceRequestDate: ${priceRequest.date}")
        for (requestedAsset in priceRequest.assets) {
            val asset = assetService.findLocally(requestedAsset.market, requestedAsset.code)
            if (asset != null) {
                requestedAsset.resolvedAsset = asset
            }
        }
        return marketDataService.getPriceResponse(priceRequest)
    }

    @PostMapping("/refresh")
    fun refreshPrices() = priceRefresh.updatePrices()

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
