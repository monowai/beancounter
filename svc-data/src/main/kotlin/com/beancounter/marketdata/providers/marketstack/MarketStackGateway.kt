package com.beancounter.marketdata.providers.marketstack

import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import com.beancounter.marketdata.providers.marketstack.model.MarketStackTickerResponse
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
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
    private val log = LoggerFactory.getLogger(MarketStackGateway::class.java)

    fun getPrices(
        assetId: String,
        date: String,
        apiKey: String = "demo"
    ): MarketStackResponse =
        try {
            restClient
                .get()
                .uri("/v2/eod/{date}?symbols={assets}&access_key={apiKey}", date, assetId, apiKey)
                .retrieve()
                .body<MarketStackResponse>()
                ?: MarketStackResponse(emptyList(), null)
        } catch (e: HttpClientErrorException) {
            // DATA-5T: MarketStack answers 422 no_valid_symbols_provided when none of the
            // requested tickers are valid - e.g. a cash/fund asset priced via
            // GET /assets/{id}?includePrice=true. Degrade to an empty response so the proxy
            // fills zero-price placeholders instead of letting the 422 surface as a 500.
            // Any other client error (bad key, rate limit) still propagates.
            if (e.statusCode.value() == 422 && e.responseBodyAsString.contains("no_valid_symbols_provided")) {
                log.warn("MarketStack rejected all symbols [{}] on {} - no valid tickers", assetId, date)
                MarketStackResponse(emptyList(), null)
            } else {
                throw e
            }
        }

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