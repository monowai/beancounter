package com.beancounter.client.config

import com.beancounter.common.client.RestClientErrorHandler
import com.beancounter.common.client.SentryTracingInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Configuration for RestClient used by all jar-client services.
 */
@Configuration
class RestClientConfig {
    @Bean
    fun bcDataRestClient(
        @Value($$"${marketdata.url:http://localhost:9510/api}") baseUrl: String
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