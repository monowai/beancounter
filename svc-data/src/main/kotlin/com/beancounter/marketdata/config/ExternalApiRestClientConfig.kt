package com.beancounter.marketdata.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Configuration for RestClients used by external API integrations.
 */
@Configuration
class ExternalApiRestClientConfig {
    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 30000

        // Interactive asset-search calls (header bar). withTimeoutOrNull in the coroutine
        // fan-out can't cancel a blocking RestClient call cooperatively, so the only way to
        // cap user-visible latency is to set the underlying HTTP timeouts low. EODHD search
        // returns a small JSON array — 2s connect / 3s read is plenty when the provider is
        // healthy and forces a fast failure when it isn't.
        private const val SEARCH_CONNECT_TIMEOUT_MS = 2000
        private const val SEARCH_READ_TIMEOUT_MS = 3000
    }

    private fun createRequestFactory(
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): SimpleClientHttpRequestFactory =
        SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(connectTimeoutMs)
            setReadTimeout(readTimeoutMs)
        }

    private fun buildRestClient(
        baseUrl: String,
        connectTimeoutMs: Int = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Int = READ_TIMEOUT_MS
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(createRequestFactory(connectTimeoutMs, readTimeoutMs))
            .build()

    @Bean
    fun alphaVantageRestClient(
        @Value($$"${beancounter.market.providers.alpha.url:https://www.alphavantage.co}") baseUrl: String
    ): RestClient = buildRestClient(baseUrl)

    @Bean
    fun exchangeRatesRestClient(
        @Value($$"${beancounter.market.providers.fx.url:https://api.exchangeratesapi.io}") baseUrl: String
    ): RestClient = buildRestClient(baseUrl)

    @Bean
    fun frankfurterRestClient(
        @Value($$"${beancounter.market.providers.frankfurter.url:https://api.frankfurter.app}") baseUrl: String
    ): RestClient = buildRestClient(baseUrl)

    @Bean
    fun openFigiRestClient(
        @Value($$"${beancounter.market.providers.figi.url:https://api.openfigi.com}") baseUrl: String
    ): RestClient = buildRestClient(baseUrl)

    @Bean
    fun marketStackRestClient(
        @Value($$"${beancounter.market.providers.mstack.url:https://api.marketstack.com}") baseUrl: String
    ): RestClient = buildRestClient(baseUrl)

    @Bean
    fun eodhdRestClient(
        @Value($$"${beancounter.market.providers.eodhd.url:https://eodhd.com}") baseUrl: String
    ): RestClient = buildRestClient(baseUrl)

    @Bean
    fun eodhdSearchRestClient(
        @Value($$"${beancounter.market.providers.eodhd.url:https://eodhd.com}") baseUrl: String
    ): RestClient =
        buildRestClient(
            baseUrl,
            connectTimeoutMs = SEARCH_CONNECT_TIMEOUT_MS,
            readTimeoutMs = SEARCH_READ_TIMEOUT_MS
        )
}