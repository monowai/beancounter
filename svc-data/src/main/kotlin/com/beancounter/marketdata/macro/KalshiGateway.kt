package com.beancounter.marketdata.macro

import io.github.resilience4j.retry.annotation.Retry
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.body

/**
 * Thin RestClient wrapper for the public Kalshi trade API. No authentication — event and market
 * odds are public data.
 */
@Component
class KalshiGateway(
    @Qualifier("kalshiRestClient")
    private val restClient: RestClient
) {
    /**
     * Open events for a series (e.g. `KXFEDDECISION` — Fed rate-decision markets).
     *
     * GET /trade-api/v2/events?series_ticker={seriesTicker}&status={status}
     */
    @Retry(name = "providerHttp")
    fun getEvents(
        seriesTicker: String,
        status: String = "open"
    ): KalshiEventsResponse =
        restClient
            .get()
            .uri(
                "/trade-api/v2/events?series_ticker={seriesTicker}&status={status}",
                seriesTicker,
                status
            ).retrieve()
            .body<KalshiEventsResponse>()
            ?: KalshiEventsResponse()

    /**
     * Open markets (outcomes) for one event.
     *
     * GET /trade-api/v2/markets?event_ticker={eventTicker}&status={status}
     */
    @Retry(name = "providerHttp")
    fun getMarkets(
        eventTicker: String,
        status: String = "open"
    ): KalshiMarketsResponse =
        restClient
            .get()
            .uri(
                "/trade-api/v2/markets?event_ticker={eventTicker}&status={status}",
                eventTicker,
                status
            ).retrieve()
            .body<KalshiMarketsResponse>()
            ?: KalshiMarketsResponse()
}