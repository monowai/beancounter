package com.beancounter.client.services

import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.time.LocalDate

/**
 * Client exposed Portfolio functions.
 */
@Service
class PortfolioServiceClient(
    @Qualifier("bcDataRestClient")
    private val restClient: RestClient,
    private val tokenService: TokenService
) {
    fun getPortfolioByCode(portfolioCode: String): Portfolio {
        val response =
            restClient
                .get()
                .uri("/api/portfolios/code/{code}", portfolioCode)
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(PortfolioResponse::class.java)
        return getOrThrow(portfolioCode, response)
    }

    fun getPortfolioById(portfolioId: String): Portfolio = getPortfolioById(portfolioId, tokenService.bearerToken)

    fun getPortfolioById(
        portfolioId: String,
        bearerToken: String
    ): Portfolio {
        val response =
            restClient
                .get()
                .uri("/api/portfolios/{id}", portfolioId)
                .header(HttpHeaders.AUTHORIZATION, bearerToken)
                .retrieve()
                .body(PortfolioResponse::class.java)
        return getOrThrow(portfolioId, response)
    }

    val portfolios: PortfoliosResponse
        get() =
            restClient
                .get()
                .uri("/api/portfolios")
                .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
                .retrieve()
                .body(PortfoliosResponse::class.java)
                ?: throw BusinessException("Failed to retrieve portfolios")

    fun add(portfoliosRequest: PortfoliosRequest): PortfoliosResponse =
        restClient
            .post()
            .uri("/api/portfolios")
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .contentType(MediaType.APPLICATION_JSON)
            .body(portfoliosRequest)
            .retrieve()
            .body(PortfoliosResponse::class.java)
            ?: throw BusinessException("Failed to add portfolios")

    fun getWhereHeld(
        assetId: String,
        tradeDate: LocalDate
    ): PortfoliosResponse =
        restClient
            .get()
            .uri("/api/portfolios/asset/{assetId}/{tradeDate}", assetId, tradeDate.toString())
            .header(HttpHeaders.AUTHORIZATION, tokenService.bearerToken)
            .retrieve()
            .body(PortfoliosResponse::class.java)
            ?: throw BusinessException("Failed to get where held")

    private fun getOrThrow(
        portfolioCode: String?,
        response: PortfolioResponse?
    ): Portfolio {
        if (response?.data == null) {
            throw BusinessException(
                String.format(
                    "Unable to find portfolio %s",
                    portfolioCode
                )
            )
        }
        return response.data
    }
}