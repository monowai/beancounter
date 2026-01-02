package com.beancounter.agent.client

import com.beancounter.common.contracts.FxResponse
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.exception.NotFoundException
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.core.ParameterizedTypeReference
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient

/**
 * RestClient for Data MCP service.
 */
@Component
class DataMcpClient(
    @Qualifier("dataMcpRestClient")
    private val restClient: RestClient
) {
    fun ping(): Map<String, String> =
        restClient
            .get()
            .uri("/ping")
            .retrieve()
            .body(object : ParameterizedTypeReference<Map<String, String>>() {})
            ?: emptyMap()

    fun getPortfolio(portfolioId: String): Portfolio =
        restClient
            .get()
            .uri("/portfolio/{portfolioId}", portfolioId)
            .retrieve()
            .body(Portfolio::class.java)
            ?: throw NotFoundException("Portfolio not found: $portfolioId")

    fun getPortfolioByCode(code: String): Portfolio =
        restClient
            .get()
            .uri("/portfolio/code/{code}", code)
            .retrieve()
            .body(Portfolio::class.java)
            ?: throw NotFoundException("Portfolio not found: $code")

    fun getPortfolios(): PortfoliosResponse =
        restClient
            .get()
            .uri("/portfolios")
            .retrieve()
            .body(PortfoliosResponse::class.java)
            ?: throw BusinessException("Failed to get portfolios")

    fun findOrCreateAsset(
        market: String,
        code: String,
        category: String
    ): Asset =
        restClient
            .post()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/asset/find-or-create")
                    .queryParam("market", market)
                    .queryParam("code", code)
                    .queryParam("category", category)
                    .build()
            }.contentType(MediaType.APPLICATION_JSON)
            .retrieve()
            .body(Asset::class.java)
            ?: throw BusinessException("Failed to find or create asset")

    fun getAsset(assetId: String): Asset =
        restClient
            .get()
            .uri("/asset/{assetId}", assetId)
            .retrieve()
            .body(Asset::class.java)
            ?: throw NotFoundException("Asset not found: $assetId")

    fun getMarketData(
        assetId: String,
        date: String
    ): Map<String, Any> =
        restClient
            .get()
            .uri { uriBuilder ->
                uriBuilder
                    .path("/asset/{assetId}/market-data")
                    .queryParam("date", date)
                    .build(assetId)
            }.retrieve()
            .body(object : ParameterizedTypeReference<Map<String, Any>>() {})
            ?: emptyMap()

    fun getFxRates(
        fromCurrency: String,
        toCurrency: String,
        rateDate: String? = null
    ): FxResponse =
        restClient
            .get()
            .uri { uriBuilder ->
                val builder =
                    uriBuilder
                        .path("/fx-rates")
                        .queryParam("fromCurrency", fromCurrency)
                        .queryParam("toCurrency", toCurrency)
                if (rateDate != null) {
                    builder.queryParam("rateDate", rateDate)
                }
                builder.build()
            }.retrieve()
            .body(FxResponse::class.java)
            ?: throw BusinessException("Failed to get FX rates")

    fun getMarkets(): MarketResponse =
        restClient
            .get()
            .uri("/markets")
            .retrieve()
            .body(MarketResponse::class.java)
            ?: throw BusinessException("Failed to get markets")

    fun getCurrencies(): Array<Currency> =
        restClient
            .get()
            .uri("/currencies")
            .retrieve()
            .body(Array<Currency>::class.java)
            ?: emptyArray()
}