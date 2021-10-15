package com.beancounter.client.services

import com.beancounter.auth.common.TokenService
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import java.time.LocalDate

/**
 * Client exposed Portfolio functions.
 */
@Service
class PortfolioServiceClient(private val portfolioGw: PortfolioGw, private val tokenService: TokenService) {
    private val dateUtils = DateUtils()
    fun getPortfolioByCode(portfolioCode: String): Portfolio {
        val response: PortfolioResponse? = portfolioGw.getPortfolioByCode(tokenService.bearerToken, portfolioCode)
        return getOrThrow(portfolioCode, response)
    }

    fun getPortfolioById(portfolioId: String): Portfolio {
        val response: PortfolioResponse? = portfolioGw.getPortfolioById(tokenService.bearerToken, portfolioId)
        return getOrThrow(portfolioId, response)
    }

    val portfolios: PortfoliosResponse
        get() = portfolioGw.getPortfolios(tokenService.bearerToken)

    fun add(portfoliosRequest: PortfoliosRequest?): PortfoliosResponse {
        return portfolioGw.addPortfolios(tokenService.bearerToken, portfoliosRequest)
    }

    fun getWhereHeld(assetId: String, tradeDate: LocalDate): PortfoliosResponse {
        return portfolioGw.getWhereHeld(
            tokenService.bearerToken,
            assetId,
            tradeDate.toString()
        )
    }

    private fun getOrThrow(portfolioCode: String?, response: PortfolioResponse?): Portfolio {
        if (response?.data == null) {
            throw BusinessException(String.format("Unable to find portfolio %s", portfolioCode))
        }
        return response.data
    }

    @FeignClient(name = "portfolios", url = "\${marketdata.url:http://localhost:9510/api}")
    /**
     * BC-DATA api calls to obtain portfolio data.
     */
    interface PortfolioGw {
        @GetMapping(value = ["/portfolios"], produces = [MediaType.APPLICATION_JSON_VALUE])
        fun getPortfolios(
            @RequestHeader("Authorization") bearerToken: String?
        ): PortfoliosResponse

        @GetMapping(value = ["/portfolios/{id}"], produces = [MediaType.APPLICATION_JSON_VALUE])
        fun getPortfolioById(
            @RequestHeader("Authorization") bearerToken: String?,
            @PathVariable("id") id: String?
        ): PortfolioResponse?

        @GetMapping(value = ["/portfolios/code/{code}"], produces = [MediaType.APPLICATION_JSON_VALUE])
        fun getPortfolioByCode(
            @RequestHeader("Authorization") bearerToken: String?,
            @PathVariable("code") code: String?
        ): PortfolioResponse?

        @GetMapping(value = ["/portfolios/asset/{assetId}/{tradeDate}"], produces = [MediaType.APPLICATION_JSON_VALUE])
        fun getWhereHeld(
            @RequestHeader("Authorization") bearerToken: String?,
            @PathVariable("assetId") assetId: String?,
            @PathVariable("tradeDate") tradeDate: String?
        ): PortfoliosResponse

        @PostMapping(value = ["/portfolios"], produces = [MediaType.APPLICATION_JSON_VALUE])
        fun addPortfolios(
            @RequestHeader("Authorization") bearerToken: String?,
            portfoliosRequest: PortfoliosRequest?
        ): PortfoliosResponse
    }
}
