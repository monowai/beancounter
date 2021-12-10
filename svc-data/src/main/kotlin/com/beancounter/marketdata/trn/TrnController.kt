package com.beancounter.marketdata.trn

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.opencsv.CSVWriterBuilder
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import javax.servlet.http.HttpServletResponse

/**
 * MVC controller for Transaction related operations.
 */
@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class TrnController(
    var trnService: TrnService,
    var trnQueryService: TrnQueryService,
    var portfolioService: PortfolioService,
    var dateUtils: DateUtils,
    var trnIoDefinition: TrnIoDefinition,
) {

    @GetMapping(value = ["/portfolio/{portfolioId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun find(@PathVariable("portfolioId") portfolioId: String): TrnResponse =
        trnService.findForPortfolio(portfolioService.find(portfolioId), dateUtils.date)

    @GetMapping(value = ["/{portfolioId}/{trnId}"])
    fun find(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("trnId") trnId: String
    ): TrnResponse =
        trnService.getPortfolioTrn(portfolioService.find(portfolioId), trnId)

    @PostMapping(produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE])
    fun update(@RequestBody trnRequest: TrnRequest): TrnResponse =
        trnService.save(portfolioService.find(trnRequest.portfolioId), trnRequest)

    @PatchMapping(
        value = ["/{portfolioId}/{trnId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE], consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun patch(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("trnId") trnId: String,
        @RequestBody trnInput: TrnInput
    ): TrnResponse {
        val portfolio = portfolioService.find(portfolioId)
        // ToDo: Support moving a transaction between portfolios
        return trnService.patch(portfolio, trnId, trnInput)
    }

    @DeleteMapping(value = ["/portfolio/{portfolioId}"])
    fun purge(@PathVariable("portfolioId") portfolioId: String): Long =
        trnService.purge(portfolioService.find(portfolioId))

    @DeleteMapping(value = ["/{trnId}"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun delete(@PathVariable("trnId") trnId: String): TrnResponse =
        trnService.delete(trnId)

    @GetMapping(value = ["/{portfolioId}/asset/{assetId}/events"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findAssetEvents(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("assetId") assetId: String
    ): TrnResponse =
        trnQueryService.findEvents(portfolioService.find(portfolioId), assetId)

    @GetMapping(value = ["/{portfolioId}/asset/{assetId}/trades"], produces = [MediaType.APPLICATION_JSON_VALUE])
    fun findAssetTrades(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("assetId") assetId: String
    ): TrnResponse =
        trnQueryService.findAssetTrades(portfolioService.find(portfolioId), assetId)

    @PostMapping(
        value = ["/query"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun findByAsset(
        @RequestBody query: TrustedTrnQuery
    ): TrnResponse =
        trnQueryService.findAssetTrades(
            query.portfolio,
            query.assetId,
            query.tradeDate
        )

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
