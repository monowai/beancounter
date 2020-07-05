package com.beancounter.marketdata.currency

import com.beancounter.auth.server.RoleHelper
import com.beancounter.common.contracts.CurrencyResponse
import org.springframework.beans.factory.annotation.Autowired
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
@PreAuthorize("hasRole('" + RoleHelper.OAUTH_USER + "')")
class CurrencyController {
    private lateinit var currencyService: CurrencyService

    @Autowired
    fun setCurrencyService(currencyService: CurrencyService) {
        this.currencyService = currencyService
    }

    @get:GetMapping
    val currencies: CurrencyResponse
        get() = CurrencyResponse(currencyService.currencies)
}