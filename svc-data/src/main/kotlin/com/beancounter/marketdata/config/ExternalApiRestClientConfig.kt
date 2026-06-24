package com.beancounter.marketdata.config

import org.apache.hc.client5.http.config.ConnectionConfig
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder
import org.apache.hc.core5.util.TimeValue
import org.apache.hc.core5.util.Timeout
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.RestClient
import java.security.Security

/**
 * RestClients for external market-data / FX APIs.
 *
 * All clients use a pooled Apache HttpClient5 connection manager with keep-alive, so a
 * connection — and its already-resolved DNS — is reused across calls instead of opening a
 * fresh socket and re-resolving the host on every request. The price-refresh scheduler
 * fires a burst of ~50 per-symbol EODHD calls; with the previous JDK request factory each
 * one re-resolved the host, and under k8s `ndots:5` search expansion that storm overwhelmed
 * CoreDNS, yielding UnknownHostException for the whole batch.
 */
@Configuration
class ExternalApiRestClientConfig {
    init {
        // A single transient DNS miss must not poison every later lookup: the JVM
        // negative-DNS cache (default ~10s) turned one CoreDNS UDP drop into a whole batch
        // of UnknownHostException. Disable negative caching so each call re-resolves.
        Security.setProperty("networkaddress.cache.negative.ttl", "0")
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000L
        private const val READ_TIMEOUT_MS = 30000L

        // Interactive asset-search calls (header bar). withTimeoutOrNull in the coroutine
        // fan-out can't cancel a blocking RestClient call cooperatively, so the only way to
        // cap user-visible latency is to set the underlying HTTP timeouts low. EODHD search
        // returns a small JSON array — 2s connect / 3s read is plenty when the provider is
        // healthy and forces a fast failure when it isn't.
        private const val SEARCH_CONNECT_TIMEOUT_MS = 2000L
        private const val SEARCH_READ_TIMEOUT_MS = 3000L

        private const val MAX_CONN_TOTAL = 40
        private const val MAX_CONN_PER_ROUTE = 20
        private const val CONNECTION_TTL_MINUTES = 5L
        private const val IDLE_EVICT_SECONDS = 30L
    }

    private fun pooledRequestFactory(
        connectTimeoutMs: Long,
        readTimeoutMs: Long
    ): HttpComponentsClientHttpRequestFactory {
        val connectionManager =
            PoolingHttpClientConnectionManagerBuilder
                .create()
                .setDefaultConnectionConfig(
                    ConnectionConfig
                        .custom()
                        .setConnectTimeout(Timeout.ofMilliseconds(connectTimeoutMs))
                        .setSocketTimeout(Timeout.ofMilliseconds(readTimeoutMs))
                        .setTimeToLive(TimeValue.ofMinutes(CONNECTION_TTL_MINUTES))
                        .build()
                ).setMaxConnTotal(MAX_CONN_TOTAL)
                .setMaxConnPerRoute(MAX_CONN_PER_ROUTE)
                .build()
        val httpClient =
            HttpClients
                .custom()
                .setConnectionManager(connectionManager)
                .evictIdleConnections(TimeValue.ofSeconds(IDLE_EVICT_SECONDS))
                .evictExpiredConnections()
                .build()
        return HttpComponentsClientHttpRequestFactory(httpClient)
    }

    private fun buildRestClient(
        baseUrl: String,
        connectTimeoutMs: Long = CONNECT_TIMEOUT_MS,
        readTimeoutMs: Long = READ_TIMEOUT_MS
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(pooledRequestFactory(connectTimeoutMs, readTimeoutMs))
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