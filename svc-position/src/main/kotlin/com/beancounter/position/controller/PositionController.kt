package com.beancounter.position.controller

import com.beancounter.auth.server.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.position.service.Valuation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * Obtain positions
 *
 * @author mikeh
 * @since 2019-02-01
 */
@RestController
@RequestMapping
@CrossOrigin("*")
@PreAuthorize(value = "hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class PositionController constructor(private val portfolioServiceClient: PortfolioServiceClient) {
    private lateinit var valuationService: Valuation

    @Autowired
    fun setValuationService(valuationService: Valuation) {
        this.valuationService = valuationService
    }

    @GetMapping(value = ["/id/{id}/{valuationDate}"], produces = ["application/json"])
    fun byId(
        @PathVariable id: String,
        @PathVariable(required = false) valuationDate: String?,
        @RequestParam(value = "value", defaultValue = "true") value: Boolean
    ): PositionResponse {
        val portfolio = portfolioServiceClient.getPortfolioById(id)
        return valuationService.getPositions(portfolio, valuationDate, value)
    }

    @GetMapping(value = ["/{code}/{valuationDate}"], produces = ["application/json"])
    fun get(
        @PathVariable code: String,
        @PathVariable(required = false) valuationDate: String?,
        @RequestParam(value = "value", defaultValue = "true") value: Boolean
    ): PositionResponse {
        val portfolio = portfolioServiceClient.getPortfolioByCode(code)
        return valuationService.getPositions(portfolio, valuationDate, value)
    }

    @PostMapping(value = ["/query"], consumes = ["application/json"], produces = ["application/json"])
    fun query(@RequestBody trnQuery: TrustedTrnQuery): PositionResponse {
        return valuationService.build(trnQuery)
    }
}
