package com.beancounter.marketdata.broker

import com.beancounter.auth.model.AuthConstants
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** The catalogue of brokerage firms, the same for every user. */
@RestController
@RequestMapping("/global-brokers")
@PreAuthorize(AuthConstants.USER_OR_SYSTEM)
class GlobalBrokerController(
    private val globalBrokerService: GlobalBrokerService
) {
    @GetMapping
    fun getGlobalBrokers(): GlobalBrokersResponse = GlobalBrokersResponse(globalBrokerService.findAll())
}