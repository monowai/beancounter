package com.beancounter.marketdata.macro

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * Response DTOs for the public (no-auth) Kalshi trade API.
 *
 * `ignoreUnknown = true` throughout — Kalshi ships many more fields than BC needs (rules text,
 * strike details, ...) and the contract isn't ours to pin.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KalshiEventsResponse(
    val events: List<KalshiEvent> = emptyList()
)

/**
 * `GET /trade-api/v2/events?series_ticker={series}&status=open`
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KalshiEvent(
    @JsonProperty("event_ticker")
    val eventTicker: String = "",
    val title: String? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class KalshiMarketsResponse(
    val markets: List<KalshiMarket> = emptyList()
)

/**
 * `GET /trade-api/v2/markets?event_ticker={eventTicker}&status=open`
 *
 * Dollar fields (`*_dollars`, `volume_24h_fp`) arrive as either a JSON string or number in the
 * 0..1 range, and may be null when a market has no quotes yet — Jackson's default scalar coercion
 * handles the string-or-number shape into [BigDecimal] without a custom deserializer.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class KalshiMarket(
    val ticker: String = "",
    @JsonProperty("yes_sub_title")
    val yesSubTitle: String? = null,
    val subtitle: String? = null,
    @JsonProperty("close_time")
    val closeTime: OffsetDateTime? = null,
    @JsonProperty("last_price_dollars")
    val lastPriceDollars: BigDecimal? = null,
    @JsonProperty("yes_bid_dollars")
    val yesBidDollars: BigDecimal? = null,
    @JsonProperty("yes_ask_dollars")
    val yesAskDollars: BigDecimal? = null,
    @JsonProperty("volume_24h_fp")
    val volume24hFp: BigDecimal? = null
)