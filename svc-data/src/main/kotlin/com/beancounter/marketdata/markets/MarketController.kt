package com.beancounter.marketdata.markets

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.MarketResponse
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/markets")
@CrossOrigin
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class MarketController internal constructor(private val marketService: MarketService) {
    @get:GetMapping
    val markets: MarketResponse
        get() = marketService.getMarkets()

    @GetMapping(value = ["/{code}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMarket(@PathVariable code: String): MarketResponse = MarketResponse(setOf(marketService.getMarket(code)))
}
