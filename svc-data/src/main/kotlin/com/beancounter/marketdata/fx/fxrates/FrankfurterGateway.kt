package com.beancounter.marketdata.fx.fxrates

import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * API calls to frankfurter.dev - a free, unlimited FX rate service based on ECB data.
 * No API key required.
 */
@Component
class FrankfurterGateway(
    @Qualifier("frankfurterRestClient")
    private val restClient: RestClient
) {
    fun getRatesForSymbols(
        date: String,
        base: String,
        symbols: String
    ): FrankfurterResponse? =
        restClient
            .get()
            .uri(
                "/{date}?base={base}&symbols={symbols}",
                date,
                base,
                symbols
            ).retrieve()
            .body<FrankfurterResponse>()
}