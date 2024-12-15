package com.beancounter.marketdata.trn

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.input.TrnInput
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.portfolio.PortfolioService
import com.opencsv.CSVWriterBuilder
import jakarta.servlet.http.HttpServletResponse
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

/**
 * MVC controller for Transaction related operations.
 */
@RestController
@RequestMapping("/trns")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
class TrnController(
    var trnService: TrnService,
    var trnQueryService: TrnQueryService,
    var portfolioService: PortfolioService,
    var dateUtils: DateUtils,
    var trnIoDefinition: TrnIoDefinition
) {
    @GetMapping(
        value = ["/portfolio/{portfolioId}/{asAt}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun findAsAt(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable asAt: String = dateUtils.today()
    ): TrnResponse =
        TrnResponse(
            trnService.findForPortfolio(
                portfolioService.find(portfolioId),
                dateUtils.getFormattedDate(asAt)
            )
        )

    @GetMapping(value = ["/{trnId}"])
    fun find(
        // @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("trnId") trnId: String
    ): TrnResponse =
        TrnResponse(
            trnService.getPortfolioTrn(
                // portfolioService.find(portfolioId),
                trnId
            )
        )

    @PostMapping(
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun update(
        @RequestBody trnRequest: TrnRequest
    ): TrnResponse =
        TrnResponse(
            trnService.save(
                portfolioService.find(trnRequest.portfolioId),
                trnRequest
            )
        )

    @PatchMapping(
        value = ["/{portfolioId}/{trnId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE],
        consumes = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun patch(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("trnId") trnId: String,
        @RequestBody trnInput: TrnInput
    ): TrnResponse {
        val portfolio = portfolioService.find(portfolioId)
        // ToDo: Support moving a transaction between portfolios
        return trnService.patch(
            portfolio,
            trnId,
            trnInput
        )
    }

    @DeleteMapping(value = ["/portfolio/{portfolioId}"])
    fun purge(
        @PathVariable("portfolioId") portfolioId: String
    ): Long = trnService.purge(portfolioService.find(portfolioId))

    @DeleteMapping(
        value = ["/{trnId}"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun delete(
        @PathVariable("trnId") trnId: String
    ): TrnResponse = TrnResponse(trnService.delete(trnId))

    @GetMapping(
        value = ["/{portfolioId}/asset/{assetId}/events"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun findAssetEvents(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("assetId") assetId: String
    ): TrnResponse =
        TrnResponse(
            trnQueryService.findEvents(
                portfolioService.find(portfolioId),
                assetId
            )
        )

    @GetMapping(
        value = ["/{portfolioId}/asset/{assetId}/trades"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun findAssetTrades(
        @PathVariable("portfolioId") portfolioId: String,
        @PathVariable("assetId") assetId: String
    ): TrnResponse =
        TrnResponse(
            trnQueryService.findAssetTrades(
                portfolioService.find(portfolioId),
                assetId
            )
        )

    @PostMapping(
        value = ["/query"]
    )
    fun findByAsset(
        @RequestBody query: TrustedTrnQuery
    ): TrnResponse =
        TrnResponse(
            trnQueryService.findAssetTrades(
                query.portfolio,
                query.assetId,
                query.tradeDate
            )
        )

    @GetMapping(value = ["/portfolio/{portfolioId}/export"])
    fun export(
        response: HttpServletResponse,
        @PathVariable("portfolioId") portfolioId: String
    ) {
        val portfolio = portfolioService.find(portfolioId)
        response.contentType = MediaType.TEXT_PLAIN_VALUE
        response.setHeader(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"${portfolio.code}.csv\""
        )
        val trnResponse =
            trnService.findForPortfolio(
                portfolio,
                dateUtils.date
            )
        val csvWriter =
            CSVWriterBuilder(response.writer)
                .withSeparator(',')
                .build()
        csvWriter.writeNext(
            trnIoDefinition.headers(),
            false
        )
        for (datum in trnResponse) {
            csvWriter.writeNext(
                trnIoDefinition.export(datum),
                false
            )
        }
        csvWriter.close()
    }
}