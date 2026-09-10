package com.beancounter.marketdata.macro

import com.beancounter.common.utils.DateUtils
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Fed rate-decision market odds from the public Kalshi API (`KXFEDDECISION` series).
 *
 * [getRateExpectations] is read-only and provider-fresh (no caching — Kalshi is public, no rate
 * limit shared with anything else BC calls). [snapshot] persists one [MacroObservation] row per
 * outcome so a later call can compute a trend delta; the public Kalshi API exposes no history of
 * its own for a market's odds over time.
 */
@Service
class RateExpectationsService(
    private val kalshiGateway: KalshiGateway,
    private val macroObservationRepo: MacroObservationRepo,
    private val dateUtils: DateUtils = DateUtils()
) {
    fun getRateExpectations(): RateExpectationsResponse? {
        val candidate = nearestOpenEvent() ?: return null
        val outcomes = candidate.markets.mapNotNull { toOutcome(it) }.sortedByDescending { it.probability }
        if (outcomes.isEmpty()) return null

        val trend = buildTrend(candidate.event.eventTicker, outcomes)
        return RateExpectationsResponse(
            event = candidate.event.eventTicker,
            title = candidate.event.title?.takeIf { it.isNotBlank() } ?: candidate.event.eventTicker,
            closeTime = candidate.closeTime,
            outcomes = outcomes,
            trend = trend.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * Snapshot current odds for the nearest open event into [MacroObservation] rows. Called by
     * [MacroRefreshSchedule]. A no-op when there's no current event/outcome to sample — never
     * throws for "nothing to snapshot yet".
     */
    fun snapshot() {
        val candidate = nearestOpenEvent() ?: return
        val outcomes = candidate.markets.mapNotNull { toOutcome(it) }
        if (outcomes.isEmpty()) return

        val now = LocalDateTime.now(dateUtils.zoneId)
        val series = seriesFor(candidate.event.eventTicker)
        for (outcome in outcomes) {
            macroObservationRepo.save(MacroObservation(series, outcome.label, outcome.probability, now))
        }
    }

    /**
     * The open KXFEDDECISION event whose nearest market close_time is soonest in the future.
     * Kalshi's `/events` response carries no close_time itself, so each candidate event's markets
     * are fetched to discover it — the winning event's markets are reused for outcome projection,
     * so this costs exactly one `/markets` call per open event, not two.
     */
    private fun nearestOpenEvent(): CandidateEvent? {
        val events = kalshiGateway.getEvents(SERIES_TICKER).events
        if (events.isEmpty()) return null

        val now = OffsetDateTime.now(dateUtils.zoneId)
        return events
            .mapNotNull { event ->
                val markets = kalshiGateway.getMarkets(event.eventTicker).markets
                val closeTime = markets.mapNotNull { it.closeTime }.filter { it.isAfter(now) }.minOrNull()
                closeTime?.let { CandidateEvent(event, markets, it) }
            }.minByOrNull { it.closeTime }
    }

    private fun toOutcome(market: KalshiMarket): RateOutcome? {
        val label =
            market.yesSubTitle?.takeIf { it.isNotBlank() }
                ?: market.subtitle?.takeIf { it.isNotBlank() }
                ?: return null
        val probability = probabilityFor(market) ?: return null
        return RateOutcome(label = label, probability = probability, volume24h = market.volume24hFp)
    }

    private fun probabilityFor(market: KalshiMarket): BigDecimal? {
        val bid = market.yesBidDollars
        val ask = market.yesAskDollars
        return when {
            bid != null && ask != null -> bid.add(ask).divide(TWO, PROBABILITY_SCALE, RoundingMode.HALF_UP)
            market.lastPriceDollars != null -> market.lastPriceDollars
            else -> null
        }
    }

    private fun buildTrend(
        eventTicker: String,
        outcomes: List<RateOutcome>
    ): List<RateTrend> {
        val cutoff = LocalDateTime.now(dateUtils.zoneId).minusDays(TREND_MIN_AGE_DAYS)
        val series = seriesFor(eventTicker)
        return outcomes.mapNotNull { outcome ->
            val prior =
                macroObservationRepo.findFirstBySeriesAndMetricAndObservedAtLessThanEqualOrderByObservedAtDesc(
                    series,
                    outcome.label,
                    cutoff
                ) ?: return@mapNotNull null
            RateTrend(
                label = outcome.label,
                probabilityPrior = prior.value,
                observedAt = prior.observedAt,
                change = outcome.probability.subtract(prior.value)
            )
        }
    }

    private fun seriesFor(eventTicker: String): String = "$SERIES_PREFIX$eventTicker"

    private data class CandidateEvent(
        val event: KalshiEvent,
        val markets: List<KalshiMarket>,
        val closeTime: OffsetDateTime
    )

    companion object {
        private const val SERIES_TICKER = "KXFEDDECISION"
        private const val SERIES_PREFIX = "KALSHI:"
        private const val TREND_MIN_AGE_DAYS = 6L
        private const val PROBABILITY_SCALE = 6
        private val TWO = BigDecimal(2)
    }
}