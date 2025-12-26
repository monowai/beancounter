package com.beancounter.marketdata.providers.alpha

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * API calls to AlphaVantage using RestClient.
 */
@Component
class AlphaGateway(
    @Qualifier("alphaVantageRestClient")
    private val restClient: RestClient
) {
    fun getCurrent(
        assetId: String,
        apiKey: String
    ): String =
        restClient
            .get()
            .uri("/query?function=GLOBAL_QUOTE&symbol={assetId}&apikey={apiKey}", assetId, apiKey)
            .retrieve()
            .body<String>()
            ?: ""

    fun getHistoric(
        assetId: String?,
        apiKey: String?
    ): String =
        restClient
            .get()
            .uri("/query?function=TIME_SERIES_DAILY&symbol={assetId}&apikey={apiKey}", assetId, apiKey)
            .retrieve()
            .body<String>()
            ?: ""

    fun getAdjusted(
        assetId: String?,
        apiKey: String?
    ): String =
        restClient
            .get()
            .uri(
                "/query?function=TIME_SERIES_DAILY_ADJUSTED&symbol={assetId}&apikey={apiKey}&outputsize=full",
                assetId,
                apiKey
            ).retrieve()
            .body<String>()
            ?: ""

    fun search(
        symbol: String?,
        apiKey: String?
    ): String =
        restClient
            .get()
            .uri("/query?function=SYMBOL_SEARCH&keywords={symbol}&apikey={apiKey}", symbol, apiKey)
            .retrieve()
            .body(String::class.java)
            ?: ""

    /**
     * Get company overview including sector and industry.
     * Used for Equity classification.
     */
    fun getOverview(
        symbol: String,
        apiKey: String
    ): String =
        restClient
            .get()
            .uri("/query?function=OVERVIEW&symbol={symbol}&apikey={apiKey}", symbol, apiKey)
            .retrieve()
            .body<String>()
            ?: ""

    /**
     * Get ETF profile including sector allocations.
     * Used for ETF exposure classification.
     */
    fun getEtfProfile(
        symbol: String,
        apiKey: String
    ): String =
        restClient
            .get()
            .uri("/query?function=ETF_PROFILE&symbol={symbol}&apikey={apiKey}", symbol, apiKey)
            .retrieve()
            .body<String>()
            ?: ""
}