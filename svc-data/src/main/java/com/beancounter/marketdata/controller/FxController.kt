package com.beancounter.marketdata.controller

import com.beancounter.auth.server.RoleHelper
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import com.beancounter.marketdata.service.FxRateService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Market Data MVC.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/fx")
@CrossOrigin
@PreAuthorize("hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
class FxController @Autowired internal constructor(private val fxRateService: FxRateService) {
    @PostMapping
    fun getRates(@RequestBody fxRequest: FxRequest): FxResponse {
        return fxRateService.getRates(fxRequest)
    }

}