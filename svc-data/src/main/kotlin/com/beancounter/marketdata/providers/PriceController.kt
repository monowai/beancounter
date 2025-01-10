package com.beancounter.marketdata.providers

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.OffMarketPriceRequest
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.DateUtils.Companion.TODAY
import com.beancounter.marketdata.providers.alpha.AlphaEventService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/prices")
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
class PriceController(
    private val marketDataService: MarketDataService,
    private val priceRefresh: PriceRefresh,
    private val eventService: AlphaEventService,
    private val priceService: PriceService,
    private val dateUtils: DateUtils
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
    ): PriceResponse = marketDataService.getPriceResponse(marketCode, assetCode)

    @GetMapping(value = ["/{assetId}"])
    fun getPrice(
        @PathVariable("assetId") id: String
    ): PriceResponse = marketDataService.getPriceResponse(id)

    /**
     * Market:Asset i.e. NYSE:MSFT.
     *
     * @param assetId Internal BC Asset identifier
     * @return Market Data information for the requested asset
     */
    @GetMapping(value = ["/{assetId}/events"])
    fun getEvents(
        @PathVariable("assetId") assetId: String
    ): PriceResponse = eventService.getEvents(assetId)

    @PostMapping("/write")
    fun writeOffMarketPrice(
        @RequestBody offMarketPriceRequest: OffMarketPriceRequest
    ): PriceResponse =
        PriceResponse(
            listOf(
                priceService
                    .getMarketData(
                        assetId = offMarketPriceRequest.assetId,
                        date = dateUtils.getFormattedDate(offMarketPriceRequest.date),
                        closePrice = offMarketPriceRequest.closePrice
                    ).get()
            )
        )

    @PostMapping
    fun getPrices(
        @RequestBody priceRequest: PriceRequest
    ): PriceResponse = marketDataService.getAssetPrices(priceRequest)

    @GetMapping("/refresh/{assetId}/{date}")
    fun refreshPrices(
        @PathVariable assetId: String,
        @PathVariable(required = false) date: String = TODAY
    ): PriceResponse =
        priceRefresh.refreshPrice(
            assetId,
            date
        )

    @PostMapping(
        value = ["/{assetId}/events"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun backFill(
        @PathVariable assetId: String
    ) = marketDataService.backFill(assetId)
}