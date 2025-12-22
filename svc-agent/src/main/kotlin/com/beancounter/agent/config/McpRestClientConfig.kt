package com.beancounter.agent.config

import com.beancounter.agent.TokenContextService
import com.beancounter.common.client.RestClientErrorHandler
import com.beancounter.common.client.SentryTracingInterceptor
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestClient

/**
 * Configuration for RestClients used to communicate with MCP services.
 */
@Configuration
class McpRestClientConfig {
    @Bean
    fun dataMcpRestClient(
        @Value("\${marketdata.url}") baseUrl: String,
        tokenContextService: TokenContextService
    ): RestClient =
        RestClient
            .builder()
            .baseUrl("$baseUrl/api/mcp")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(30000)
                }
            ).requestInterceptor(RestClientAuthInterceptor(tokenContextService))
            .requestInterceptor(SentryTracingInterceptor())
            .defaultStatusHandler(RestClientErrorHandler())
            .build()

    @Bean
    fun eventMcpRestClient(
        @Value("\${event.url}") baseUrl: String,
        tokenContextService: TokenContextService
    ): RestClient =
        RestClient
            .builder()
            .baseUrl("$baseUrl/api/mcp")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(30000)
                }
            ).requestInterceptor(RestClientAuthInterceptor(tokenContextService))
            .requestInterceptor(SentryTracingInterceptor())
            .defaultStatusHandler(RestClientErrorHandler())
            .build()

    @Bean
    fun positionMcpRestClient(
        @Value("\${position.url}") baseUrl: String,
        tokenContextService: TokenContextService
    ): RestClient =
        RestClient
            .builder()
            .baseUrl("$baseUrl/api/mcp")
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(30000)
                }
            ).requestInterceptor(RestClientAuthInterceptor(tokenContextService))
            .requestInterceptor(SentryTracingInterceptor())
            .defaultStatusHandler(RestClientErrorHandler())
            .build()
}