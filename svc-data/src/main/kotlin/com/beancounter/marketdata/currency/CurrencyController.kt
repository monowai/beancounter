package com.beancounter.marketdata.currency

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.CurrencyResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/currencies")
@CrossOrigin
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class CurrencyController(val currencyService: CurrencyService) {

    @get:GetMapping
    val currencies: CurrencyResponse
        get() = CurrencyResponse(currencyService.currencies)
}
