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

    fun getFullOutput(
        assetId: String,
        apiKey: String
    ): String? =
        restClient
            .get()
            .uri("/query?function=TIME_SERIES_DAILY&symbol={assetId}&apikey={apiKey}&outputsize=full", assetId, apiKey)
            .retrieve()
            .body(String::class.java)

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
}