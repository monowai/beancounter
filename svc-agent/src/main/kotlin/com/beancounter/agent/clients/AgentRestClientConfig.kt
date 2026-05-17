package com.beancounter.agent.clients

import com.beancounter.common.client.RestClientErrorHandler
import com.beancounter.common.client.SentryTracingInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * RestClient beans for services consumed *only* by the agent — specifically
 * svc-retire (bc-retire) and svc-rebalance (bc-rebalance).
 *
 * Mirrors the shape of jar-client's [com.beancounter.client.config.RestClientConfig]
 * (bcDataRestClient), with the same timeouts, Sentry tracing interceptor, and
 * error-translation handler. Base URLs default to local dev ports but are
 * overridable via the `retire.url` / `rebalance.url` properties (or the matching
 * `RETIRE_URL` / `REBALANCE_URL` env vars).
 */
@Configuration
class AgentRestClientConfig {
    @Bean
    fun retireRestClient(
        @Value($$"${retire.url:http://localhost:9540/api}") baseUrl: String
    ): RestClient = buildClient(baseUrl)

    @Bean
    fun rebalanceRestClient(
        @Value($$"${rebalance.url:http://localhost:9550/api}") baseUrl: String
    ): RestClient = buildClient(baseUrl)

    private fun buildClient(baseUrl: String): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(CONNECT_TIMEOUT_MS)
                    setReadTimeout(READ_TIMEOUT_MS)
                }
            ).requestInterceptor(SentryTracingInterceptor())
            .defaultStatusHandler(RestClientErrorHandler())
            .build()

    companion object {
        private const val CONNECT_TIMEOUT_MS = 5000
        private const val READ_TIMEOUT_MS = 30000
    }
}