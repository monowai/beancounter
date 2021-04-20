package com.beancounter.marketdata.portfolio

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.utils.DateUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * MVC controller for Portfolio activities.
 */
@RestController
@RequestMapping("/portfolios")
@PreAuthorize("hasAnyRole('" + AuthConstants.OAUTH_USER + "', '" + AuthConstants.OAUTH_M2M + "')")
class PortfolioController internal constructor(
    private val portfolioService: PortfolioService,
    private val dateUtils: DateUtils
) {

    @get:GetMapping
    val portfolios: PortfoliosResponse
        get() = PortfoliosResponse(portfolioService.portfolios)

    @GetMapping("/{id}")
    fun getPortfolio(@PathVariable id: String): PortfolioResponse {
        val portfolio = portfolioService.find(id)
        return PortfolioResponse(portfolio)
    }

    @DeleteMapping("/{id}")
    fun deletePortfolio(@PathVariable id: String): String {
        portfolioService.delete(id)
        return "ok"
    }

    @GetMapping("/code/{code}")
    fun getPortfolioByCode(@PathVariable code: String): PortfolioResponse {
        val portfolio = portfolioService.findByCode(code)
        return PortfolioResponse(portfolio)
    }

    @PatchMapping(value = ["/{id}"])
    fun savePortfolio(
        @PathVariable id: String,
        @RequestBody portfolio: PortfolioInput
    ): PortfolioResponse {
        return PortfolioResponse(portfolioService.update(id, portfolio))
    }

    @PostMapping
    fun savePortfolios(
        @RequestBody portfolio: PortfoliosRequest
    ): PortfoliosResponse {
        return PortfoliosResponse(portfolioService.save(portfolio.data))
    }

    @GetMapping(value = ["/asset/{assetId}/{tradeDate}"])
    fun getWhereHeld(
        @PathVariable("assetId") assetId: String,
        @PathVariable("tradeDate") tradeDate: String
    ): PortfoliosResponse {
        return portfolioService.findWhereHeld(assetId, dateUtils.getDate(tradeDate))
    }
}
