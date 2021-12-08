package com.beancounter.marketdata.fx

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.contracts.FxResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * FX Market Data Controller.
 *
 * @author mikeh
 * @since 2019-01-29
 */
@RestController
@RequestMapping("/fx")
@CrossOrigin
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class FxController @Autowired internal constructor(private val fxRateService: FxRateService) {
    @PostMapping
    fun getRates(@RequestBody fxRequest: FxRequest): FxResponse = fxRateService.getRates(fxRequest)
}
