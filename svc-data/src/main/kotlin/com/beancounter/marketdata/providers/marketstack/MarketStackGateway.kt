package com.beancounter.marketdata.providers.marketstack

import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import com.beancounter.marketdata.providers.marketstack.model.MarketStackTickerResponse
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
            .uri("/v2/eod/{date}?symbols={assets}&access_key={apiKey}", date, assetId, apiKey)
            .retrieve()
            .body<MarketStackResponse>()
            ?: MarketStackResponse(emptyList(), null)

    fun getHistory(
        symbol: String,
        dateFrom: String,
        dateTo: String,
        apiKey: String = "demo",
        limit: Int = 1000
    ): MarketStackResponse =
        restClient
            .get()
            .uri(
                "/v2/eod?symbols={symbol}&date_from={dateFrom}&date_to={dateTo}&access_key={apiKey}&limit={limit}",
                symbol,
                dateFrom,
                dateTo,
                apiKey,
                limit
            ).retrieve()
            .body<MarketStackResponse>()
            ?: MarketStackResponse(emptyList(), null)

    /**
     * Search for tickers on a specific exchange.
     * @param exchangeMic The MIC code of the exchange (e.g., "XSES" for SGX)
     * @param searchTerm The search keyword
     * @param apiKey MarketStack API key
     * @param limit Maximum number of results
     */
    fun searchTickers(
        exchangeMic: String,
        searchTerm: String,
        apiKey: String = "demo",
        limit: Int = 20
    ): MarketStackTickerResponse =
        restClient
            .get()
            .uri(
                "/v2/exchanges/{mic}/tickers?search={search}&access_key={apiKey}&limit={limit}",
                exchangeMic,
                searchTerm,
                apiKey,
                limit
            ).retrieve()
            .body<MarketStackTickerResponse>()
            ?: MarketStackTickerResponse()
}