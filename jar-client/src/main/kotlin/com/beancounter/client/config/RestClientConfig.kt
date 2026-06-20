package com.beancounter.client.config

import com.beancounter.common.client.RestClientErrorHandler
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Configuration for RestClient used by all jar-client services.
 *
 * Timeouts are externalised via `marketdata.client.connect-timeout-ms` and
 * `marketdata.client.read-timeout-ms`. Services that issue large batch calls
 * to bc-data (e.g. svc-position bulk price valuation) should raise the read
 * timeout in their own application.yml. Retries on transient
 * ResourceAccessException are handled by Resilience4j `@Retry(name = "bcData")`
 * at the call site — bumping the timeout reduces noise; retry covers what's
 * left.
 */
@Configuration
class RestClientConfig {
    @Bean
    fun bcDataRestClient(
        @Value($$"${marketdata.url:http://localhost:9510/api}") baseUrl: String,
        @Value($$"${marketdata.client.connect-timeout-ms:5000}") connectTimeoutMs: Int,
        @Value($$"${marketdata.client.read-timeout-ms:30000}") readTimeoutMs: Int
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(connectTimeoutMs)
                    setReadTimeout(readTimeoutMs)
                }
            ).defaultStatusHandler(RestClientErrorHandler())
            .build()
}