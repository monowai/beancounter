package com.beancounter.position.service

import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Valuation
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
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
@CrossOrigin
@PreAuthorize("hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')")
class PositionController(
    private val portfolioServiceClient: PortfolioServiceClient,
    private val dateUtils: DateUtils,
) {
    private lateinit var valuationService: Valuation

    @Autowired
    fun setValuationService(valuationService: Valuation) {
        this.valuationService = valuationService
    }

    @GetMapping(value = ["/id/{id}/{valuationDate}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun byId(
        @PathVariable id: String,
        @PathVariable(required = false) valuationDate: String = dateUtils.offsetDateString(),
        @RequestParam(value = "value", defaultValue = "true") value: Boolean,
    ): PositionResponse {
        val portfolio = portfolioServiceClient.getPortfolioById(id)
        return valuationService.getPositions(portfolio, valuationDate, value)
    }

    @GetMapping(value = ["/{code}/{valuationDate}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun get(
        @PathVariable code: String,
        @PathVariable(required = false) valuationDate: String = DateUtils.TODAY,
        @RequestParam(value = "value", defaultValue = "true") value: Boolean,
    ): PositionResponse {
        log.debug("valuationDate: $valuationDate")
        val portfolio = portfolioServiceClient.getPortfolioByCode(code)
        return valuationService.getPositions(portfolio, valuationDate, value)
    }

    @PostMapping(
        value = ["/query"],
        consumes = [MediaType.APPLICATION_JSON_VALUE],
        produces = [MediaType.APPLICATION_JSON_VALUE],
    )
    fun query(
        @RequestBody trnQuery: TrustedTrnQuery,
    ): PositionResponse {
        return valuationService.build(trnQuery)
    }

    companion object {
        private val log = LoggerFactory.getLogger(this::class.java)
    }
}
