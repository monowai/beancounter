package com.beancounter.marketdata.providers.marketstack

import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * API calls to MarketStack using RestClient.
 */
@Component
class MarketStackGateway(
    @Qualifier("marketStackRestClient")
    private val restClient: RestClient
) {
    fun getPrices(
        assetId: String,
        date: String,
        apiKey: String = "demo"
    ): MarketStackResponse =
        restClient
            .get()
            .uri("/v1/eod/{date}?symbols={assets}&access_key={apiKey}", date, assetId, apiKey)
            .retrieve()
            .body<MarketStackResponse>()
            ?: MarketStackResponse(emptyList(), null)
}