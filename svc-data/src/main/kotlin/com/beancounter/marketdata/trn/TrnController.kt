package com.beancounter.marketdata.trn

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.exception.SystemException
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.opencsv.CSVWriterBuilder
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse

@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
/**
 * MVC controller for Transaction related operations.
 */
class TrnController {
    private lateinit var trnService: TrnService
    private lateinit var portfolioService: PortfolioService
    private lateinit var dateUtils: DateUtils
    private lateinit var trnIoDefinition: TrnIoDefinition

    @Autowired
    fun setServices(
        trnService: TrnService,
        portfolioService: PortfolioService,
        dateUtils: DateUtils,
        trnIoDefinition: TrnIoDefinition,
    ) {
        this.trnService = trnService
        this.portfolioService = portfolioService
        this.dateUtils = dateUtils
        this.trnIoDefinition = trnIoDefinition
    }

    @GetMapping(value = ["/portfolio/{portfolioId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun find(@PathVariable("portfolioId") portfolioId: String): TrnResponse {
        val portfolio = portfolioService.find(portfolioId)
        return trnService.findForPortfolio(portfolio, dateUtils.date)
    }

    @GetMapping(value = ["/{portfolioId}/{trnId}"])
    fun find(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("trnId") trnId: String
    ): TrnResponse {
        val portfolio = portfolioService.find(portfolioId)
        return trnService.getPortfolioTrn(portfolio, trnId)
    }

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(@RequestBody trnRequest: TrnRequest): TrnResponse {
        val portfolio = portfolioService.find(trnRequest.portfolioId)
        return trnService.save(portfolio, trnRequest)
    }

    @DeleteMapping(value = ["/portfolio/{portfolioId}"])
    fun purge(@PathVariable("portfolioId") portfolioId: String): Long {
        val portfolio = portfolioService.find(portfolioId)
        return trnService.purge(portfolio)
    }

    @DeleteMapping(value = ["/{trnId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun delete(@PathVariable("trnId") trnId: String): TrnResponse {
        return trnService.delete(trnId)
    }

    @GetMapping(value = ["/{portfolioId}/asset/{assetId}/events"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findAssetEvents(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("assetId") assetId: String
    ): TrnResponse {
        return trnService.findPortfolioAssetEvents(portfolioService.find(portfolioId), assetId)
    }

    @GetMapping(value = ["/{portfolioId}/asset/{assetId}/trades"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findAssetTrades(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("assetId") assetId: String
    ): TrnResponse {
        return trnService.findPortfolioAssetTrades(portfolioService.find(portfolioId), assetId)
    }

    @PostMapping(
        value = ["/query"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun findByAsset(
        @RequestBody query: TrustedTrnQuery
    ): TrnResponse {
        if (query.portfolio.id == "abc") {
            throw SystemException("You can't touch this")
        }
        return trnService.findByPortfolioAsset(
            query.portfolio,
            query.assetId,
            query.tradeDate
        )
    }

    @GetMapping(value = ["/portfolio/{portfolioId}/export"])
    fun export(response: HttpServletResponse, @PathVariable("portfolioId") portfolioId: String) {
        val portfolio = portfolioService.find(portfolioId)
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"${portfolio.code}.csv\""
        )
        val trnResponse = trnService.findForPortfolio(portfolio, dateUtils.date)
        val csvWriter = CSVWriterBuilder(response.writer)
            .withSeparator(',')
            .build()
        csvWriter.writeNext(trnIoDefinition.headers().toTypedArray(), false)
        for (datum in trnResponse.data) {
            csvWriter.writeNext(trnIoDefinition.toArray(datum), false)
        }
        csvWriter.close()
    }
}
