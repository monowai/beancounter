package com.beancounter.marketdata.macro

import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(KalshiGateway::class.java)

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
            ?: run {
                // A 200 with a null-deserialized body is contract drift, not an expected "no
                // events" shape (that's an empty `events` array) — surface it rather than
                // silently treating it the same as a genuinely empty response.
                log.warn("Kalshi events response body was null for seriesTicker={}", seriesTicker)
                KalshiEventsResponse()
            }

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
            ?: run {
                log.warn("Kalshi markets response body was null for eventTicker={}", eventTicker)
                KalshiMarketsResponse()
            }
}