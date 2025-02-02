package com.beancounter.marketdata.cash

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.AssetsResponse
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Cash MVC.
 */
@RestController
@RequestMapping("/cash")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
class CashController(
    val cashService: CashService
) {
    @GetMapping
    fun getAsset(): AssetsResponse = AssetsResponse(cashService.find())
}