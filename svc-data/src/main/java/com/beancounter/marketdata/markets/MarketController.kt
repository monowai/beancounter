package com.beancounter.marketdata.markets

import com.beancounter.auth.server.RoleHelper
import com.beancounter.common.contracts.MarketResponse
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
@RequestMapping("/markets")
@CrossOrigin
@PreAuthorize("hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
class MarketController internal constructor(private val marketService: MarketService) {
    @get:GetMapping
    val markets: MarketResponse
        get() = marketService.getMarkets()

    @GetMapping(value = ["/{code}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun getMarket(@PathVariable code: String): MarketResponse {
        val market = marketService.getMarket(code)
        return MarketResponse(setOf(market))
    }

}