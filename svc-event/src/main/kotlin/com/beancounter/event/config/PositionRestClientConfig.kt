package com.beancounter.event.config

import com.beancounter.common.client.RestClientErrorHandler
import com.beancounter.common.client.SentryTracingInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Configuration for RestClient used to communicate with svc-position.
 */
@Configuration
class PositionRestClientConfig {
    @Bean
    fun positionRestClient(
        @Value("\${position.url:http://localhost:9500}") baseUrl: String
    ): RestClient =
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