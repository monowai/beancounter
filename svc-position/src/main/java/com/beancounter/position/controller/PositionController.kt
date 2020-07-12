package com.beancounter.position.controller

import com.beancounter.auth.server.RoleHelper
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.position.service.Valuation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

/**
 * Still thinking on this.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@RestController
@RequestMapping
@CrossOrigin("*")
@PreAuthorize(value = "hasAnyRole('" + RoleHelper.OAUTH_USER + "', '" + RoleHelper.OAUTH_M2M + "')")
class PositionController
@Autowired
internal constructor(private val portfolioServiceClient: PortfolioServiceClient) {
    private lateinit var valuationService: Valuation

    @Autowired
    fun setValuationService(valuationService: Valuation) {
        this.valuationService = valuationService
    }

    @GetMapping(value = ["/{code}/{valuationDate}"], produces = ["application/json"])
    operator fun get(@PathVariable code: String,
                     @PathVariable(required = false) valuationDate: String?,
                     @RequestParam(value = "value", defaultValue = "true") value: Boolean): PositionResponse {
        val portfolio = portfolioServiceClient.getPortfolioByCode(code)
        var valDate = valuationDate
        if (valDate == null) {
            valDate = "today"
        }
        val positions = valuationService.build(portfolio, valDate).data
        return if (value) {
            valuationService.value(positions)
        } else PositionResponse(positions)
    }

    @PostMapping(value = ["/query"], consumes = ["application/json"], produces = ["application/json"])
    fun query(@RequestBody trnQuery: TrustedTrnQuery): PositionResponse {
        return valuationService.build(trnQuery)
    }

}