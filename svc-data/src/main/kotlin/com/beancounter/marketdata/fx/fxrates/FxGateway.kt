package com.beancounter.marketdata.fx.fxrates

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * API calls to exchangeratesapi.io using RestClient.
 */
@Component
class FxGateway(
    @Qualifier("exchangeRatesRestClient")
    private val restClient: RestClient,
    @Value($$"${beancounter.market.providers.fx.key:}")
    private val accessKey: String
) {
    fun getRatesForSymbols(
        date: String,
        base: String,
        symbols: String
    ): ExRatesResponse? =
        restClient
            .get()
            .uri(
                "/v1/{date}?base={base}&symbols={symbols}&access_key={accessKey}",
                date,
                base,
                symbols,
                accessKey
            ).retrieve()
            .body<ExRatesResponse>()
}