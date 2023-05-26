package com.beancounter.marketdata.providers

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.OffMarketPriceRequest
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.alpha.AlphaEventService
import com.beancounter.marketdata.registration.SystemUserService
import org.slf4j.LoggerFactory
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
@PreAuthorize("hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')")
class PriceController(
    private val marketDataService: MarketDataService,
    private val assetService: AssetService,
    private val priceRefresh: PriceRefresh,
    private val eventService: AlphaEventService,
    private val systemUserService: SystemUserService,
    private val priceService: PriceService,
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
        @PathVariable("assetCode") assetCode: String,
    ): PriceResponse {
        val asset = assetService.findLocally(AssetInput(marketCode, assetCode))
            ?: throw BusinessException(String.format("Asset not found %s/%s", marketCode, assetCode))
        return marketDataService.getPriceResponse(
            PriceRequest.of(
                AssetInput(
                    market = asset.market.code,
                    code = asset.code,
                ),
            ),
        )
    }

    @GetMapping(value = ["/{assetId}"])
    fun getPrice(
        @PathVariable("assetId") id: String,
    ): PriceResponse {
        val asset = assetService.find(id)
        return marketDataService.getPriceResponse(
            PriceRequest.of(asset),
        )
    }

    /**
     * Market:Asset i.e. NYSE:MSFT.
     *
     * @param assetId Internal BC Asset identifier
     * @return Market Data information for the requested asset
     */
    @GetMapping(value = ["/{assetId}/events"])
    fun getEvents(
        @PathVariable("assetId") assetId: String,
    ): PriceResponse {
        return eventService.getEvents(assetService.find(assetId))
    }

    @PostMapping("/write")
    fun writeOffMarketPrice(@RequestBody offMarketPriceRequest: OffMarketPriceRequest): PriceResponse {
        val asset = assetService.find(offMarketPriceRequest.assetId)
        return PriceResponse(
            listOf(
                priceService.getMarketData(
                    asset = asset,
                    date = DateUtils().getDate(offMarketPriceRequest.date),
                    closePrice = offMarketPriceRequest.closePrice,
                ).get(),
            ),
        )
    }

    @PostMapping
    fun getPrices(@RequestBody priceRequest: PriceRequest): PriceResponse {
        log.debug("priceRequestDate: ${priceRequest.date}")
        val systemUser = systemUserService.getActiveUser()
        for (priceAsset in priceRequest.assets) {
            val asset = if (priceAsset.assetId.isNotEmpty()) {
                assetService.find(priceAsset.assetId)
            } else {
                assetService.findLocally(
                    AssetInput(
                        market = priceAsset.market,
                        code = priceAsset.code,
                        owner = systemUser?.id ?: "",
                    ),
                )
            }
            if (asset != null) {
                priceAsset.resolvedAsset = asset
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
