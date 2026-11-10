package com.beancounter.marketdata.macro

import java.time.OffsetDateTime

/** An open Kalshi event with its markets and the soonest future market close time. */
data class KalshiCandidateEvent(
    val event: KalshiEvent,
    val markets: List<KalshiMarket>,
    val closeTime: OffsetDateTime
)

/**
 * The open event in [seriesTicker] whose nearest market close_time is soonest after [now].
 *
 * Kalshi's `/events` response carries no close_time itself, so each candidate event's markets are
 * fetched to discover it — the winning event's markets are returned for projection, so this costs
 * exactly one `/markets` call per open event, not two. An event whose markets have all closed (the
 * release has happened but Kalshi hasn't settled it yet) is skipped.
 */
fun KalshiGateway.nearestOpenEvent(
    seriesTicker: String,
    now: OffsetDateTime
): KalshiCandidateEvent? =
    getEvents(seriesTicker)
        .events
        .mapNotNull { event ->
            val markets = getMarkets(event.eventTicker).markets
            val closeTime = markets.mapNotNull { it.closeTime }.filter { it.isAfter(now) }.minOrNull()
            closeTime?.let { KalshiCandidateEvent(event, markets, it) }
        }.minByOrNull { it.closeTime }