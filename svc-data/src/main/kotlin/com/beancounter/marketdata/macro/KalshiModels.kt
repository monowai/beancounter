package com.beancounter.marketdata.macro

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

/**
 * Response DTOs for the public (no-auth) Kalshi trade API.
 *
 * `ignoreUnknown = true` throughout — Kalshi ships many more fields than BC needs (rules text,
 * cap strikes, ...) and the contract isn't ours to pin.
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
    val volume24hFp: BigDecimal? = null,
    /** `greater` for an "Above X" threshold market, where [floorStrike] is X. */
    @JsonProperty("strike_type")
    val strikeType: String? = null,
    @JsonProperty("floor_strike")
    val floorStrike: BigDecimal? = null
) {
    /**
     * Implied probability of YES: the mid of the yes bid and ask, falling back to the last trade
     * when either side is missing. Null when the market has no quotes and no trades.
     */
    fun impliedProbability(): BigDecimal? {
        val bid = yesBidDollars
        val ask = yesAskDollars
        return if (bid != null && ask != null) {
            bid.add(ask).divide(TWO, PROBABILITY_SCALE, RoundingMode.HALF_UP)
        } else {
            lastPriceDollars
        }
    }

    private companion object {
        const val PROBABILITY_SCALE = 6
        val TWO = BigDecimal(2)
    }
}