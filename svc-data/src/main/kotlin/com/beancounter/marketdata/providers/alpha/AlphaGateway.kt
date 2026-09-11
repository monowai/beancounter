package com.beancounter.marketdata.providers.alpha

import io.github.resilience4j.retry.annotation.Retry
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
    @Retry(name = "providerHttp")
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

    @Retry(name = "providerHttp")
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

    @Retry(name = "providerHttp")
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

    @Retry(name = "providerHttp")
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
    @Retry(name = "providerHttp")
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
    @Retry(name = "providerHttp")
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

    /**
     * Daily treasury-yield curve point for one maturity.
     *
     * GET /query?function=TREASURY_YIELD&interval=daily&maturity={maturity}&apikey={apiKey}
     *
     * `maturity` is one of AlphaVantage's maturity codes (`10year`, `2year`, ...). Response is
     * `{"name":..., "data":[{"date":"2026-09-09","value":"4.83"},...]}`, newest-first; non-trading
     * days carry `value: "."`.
     */
    @Retry(name = "providerHttp")
    fun getTreasuryYield(
        interval: String,
        maturity: String,
        apiKey: String
    ): String =
        restClient
            .get()
            .uri(
                "/query?function=TREASURY_YIELD&interval={interval}&maturity={maturity}&apikey={apiKey}",
                interval,
                maturity,
                apiKey
            ).retrieve()
            .body<String>()
            ?: ""

    @Retry(name = "providerHttp")
    fun getNewsSentiment(
        tickers: String,
        apiKey: String,
        topics: String? = null,
        limit: Int = 10,
        timeFrom: String? = null
    ): String {
        var uri = "/query?function=NEWS_SENTIMENT&tickers=$tickers&apikey=$apiKey&limit=$limit"
        if (!topics.isNullOrBlank()) {
            uri += "&topics=$topics"
        }
        if (!timeFrom.isNullOrBlank()) {
            uri += "&time_from=$timeFrom"
        }
        return restClient
            .get()
            .uri(uri)
            .retrieve()
            .body<String>()
            ?: ""
    }
}