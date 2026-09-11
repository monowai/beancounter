package com.beancounter.marketdata.macro

import com.beancounter.common.utils.DateUtils
import jakarta.transaction.Transactional
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Fed rate-decision market odds from the public Kalshi API (`KXFEDDECISION` series).
 *
 * [getRateExpectations] is cached for 10 minutes (`macro.rate.expectations`, registered in
 * `CacheConfig`) — [nearestOpenEvent] costs one Kalshi `/markets` call per open event, and
 * re-running that on every request is unnecessary N+1 traffic when odds only move gradually.
 * [snapshot] and [prune] are NOT cached: [snapshot] calls [nearestOpenEvent] directly (never
 * [getRateExpectations]), so a cache hit never short-circuits the odds actually persisted as a
 * trend sample; [prune] doesn't touch Kalshi at all. [snapshot] persists one [MacroObservation]
 * row per outcome so a later call can compute a trend delta; the public Kalshi API exposes no
 * history of its own for a market's odds over time.
 */
@Service
class RateExpectationsService(
    private val kalshiGateway: KalshiGateway,
    private val macroObservationRepo: MacroObservationRepo,
    private val dateUtils: DateUtils = DateUtils()
) {
    @Cacheable(CACHE_NAME)
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
            macroObservationRepo.save(MacroObservation(series, metricFor(outcome.label), outcome.probability, now))
        }
    }

    /**
     * Delete [MacroObservation] rows observed more than [retentionDays] days ago. Bulk JPQL delete
     * via [MacroObservationRepo.deleteByObservedAtBefore] — never loop-deletes. Called by
     * [MacroRefreshSchedule]'s prune step, which invokes this on the [RateExpectationsService] bean
     * (not self-invoked) precisely so `@Transactional` actually applies — self-invocation bypasses
     * the Spring proxy, and this bulk `@Modifying` query requires an active transaction.
     */
    @Transactional
    fun prune(retentionDays: Long): Int {
        val cutoff = LocalDateTime.now(dateUtils.zoneId).minusDays(retentionDays)
        return macroObservationRepo.deleteByObservedAtBefore(cutoff)
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
                    metricFor(outcome.label),
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

    /**
     * Defensive truncation of a Kalshi outcome label before it's used as [MacroObservation.metric]
     * — the column is VARCHAR([MAX_METRIC_LENGTH]). Applied consistently on both the persist path
     * ([snapshot]) and the lookup path ([buildTrend]) so a hypothetically over-length label still
     * round-trips to the same stored row.
     */
    private fun metricFor(label: String): String = label.take(MAX_METRIC_LENGTH)

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

        // Matches MacroObservation.metric's VARCHAR(255) column (V34 migration).
        private const val MAX_METRIC_LENGTH = 255

        const val CACHE_NAME = "macro.rate.expectations"
    }
}