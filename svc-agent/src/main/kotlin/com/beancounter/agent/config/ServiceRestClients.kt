package com.beancounter.agent.config

import com.beancounter.common.client.RestClientErrorHandler
import com.beancounter.common.client.SentryTracingInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * RestClient beans for the Beancounter services that the agent calls directly.
 *
 * `bcDataRestClient` is contributed by jar-client; this configuration only adds
 * the position and event service clients, which jar-client does not currently
 * provide. URLs include the `/api` context path, matching the convention used
 * by the rest of the Beancounter services.
 */
@Configuration
class ServiceRestClients {
    @Bean
    fun bcPositionRestClient(
        @Value($$"${position.url:http://localhost:9500/api}") baseUrl: String
    ): RestClient = build(baseUrl)

    @Bean
    fun bcEventRestClient(
        @Value($$"${event.url:http://localhost:9520/api}") baseUrl: String
    ): RestClient = build(baseUrl)

    private fun build(baseUrl: String): RestClient =
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