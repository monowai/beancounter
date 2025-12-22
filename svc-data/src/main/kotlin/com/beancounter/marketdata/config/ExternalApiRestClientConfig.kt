package com.beancounter.marketdata.config

import com.beancounter.common.client.SentryTracingInterceptor
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
    @Bean
    fun alphaVantageRestClient(
        @Value("\${beancounter.market.providers.alpha.url:https://www.alphavantage.co}") baseUrl: String
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(30000)
                }
            ).requestInterceptor(SentryTracingInterceptor())
            .build()

    @Bean
    fun exchangeRatesRestClient(
        @Value("\${beancounter.market.providers.fx.url:https://api.exchangeratesapi.io}") baseUrl: String
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(30000)
                }
            ).requestInterceptor(SentryTracingInterceptor())
            .build()

    @Bean
    fun openFigiRestClient(
        @Value("\${beancounter.market.providers.figi.url:https://api.openfigi.com}") baseUrl: String
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(30000)
                }
            ).requestInterceptor(SentryTracingInterceptor())
            .build()

    @Bean
    fun marketStackRestClient(
        @Value("\${beancounter.market.providers.mstack.url:https://api.marketstack.com}") baseUrl: String
    ): RestClient =
        RestClient
            .builder()
            .baseUrl(baseUrl)
            .requestFactory(
                SimpleClientHttpRequestFactory().apply {
                    setConnectTimeout(5000)
                    setReadTimeout(30000)
                }
            ).requestInterceptor(SentryTracingInterceptor())
            .build()
}