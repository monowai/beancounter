package com.beancounter.marketdata.portfolio

import com.beancounter.auth.model.AuthConstants
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.utils.DateUtils
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
 * Rest controller for Portfolio activities.
 */
@RestController
@RequestMapping("/portfolios")
@CrossOrigin
@PreAuthorize(
    "hasAnyAuthority('" + AuthConstants.SCOPE_USER + "', '" + AuthConstants.SCOPE_SYSTEM + "')"
)
class PortfolioController internal constructor(
    private val portfolioService: PortfolioService,
    private val dateUtils: DateUtils
) {
    @get:GetMapping
    val portfolios: PortfoliosResponse
        get() = PortfoliosResponse(portfolioService.portfolios)

    @GetMapping("/{id}")
    fun getPortfolio(
        @PathVariable id: String
    ): PortfolioResponse = PortfolioResponse(portfolioService.find(id))

    @DeleteMapping("/{id}")
    fun deletePortfolio(
        @PathVariable id: String
    ): String {
        portfolioService.delete(id)
        return "deleted $id"
    }

    @GetMapping("/code/{code}")
    fun getPortfolioByCode(
        @PathVariable code: String
    ): PortfolioResponse = PortfolioResponse(portfolioService.findByCode(code))

    @PatchMapping(value = ["/{id}"])
    fun savePortfolio(
        @PathVariable id: String,
        @RequestBody portfolio: PortfolioInput
    ): PortfolioResponse =
        PortfolioResponse(
            portfolioService.update(
                id,
                portfolio
            )
        )

    @PostMapping
    fun savePortfolios(
        @RequestBody portfolio: PortfoliosRequest
    ): PortfoliosResponse = PortfoliosResponse(portfolioService.save(portfolio.data))

    @GetMapping(value = ["/asset/{assetId}/{tradeDate}"])
    fun getWhereHeld(
        @PathVariable("assetId") assetId: String,
        @PathVariable("tradeDate") tradeDate: String
    ): PortfoliosResponse =
        portfolioService.findWhereHeld(
            assetId,
            dateUtils.getFormattedDate(tradeDate)
        )
}