package com.beancounter.marketdata.macro

import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * One market outcome (e.g. "Hike 25bps") under the nearest open Fed-decision event, with its
 * implied probability.
 */
data class RateOutcome(
    val label: String,
    val probability: BigDecimal,
    val volume24h: BigDecimal?
)

/**
 * Change in an outcome's implied probability since the nearest [MacroObservation] snapshot at
 * least 6 days old (see [RateExpectationsService]'s trend cutoff).
 */
data class RateTrend(
    val label: String,
    val probabilityPrior: BigDecimal,
    val observedAt: java.time.LocalDateTime,
    val change: BigDecimal
)

/**
 * `GET /macro/rate-expectations` response. [trend] is omitted entirely (not serialized as
 * `null`) when there's no prior snapshot to compare against.
 */
data class RateExpectationsResponse(
    val event: String,
    val title: String,
    val closeTime: OffsetDateTime,
    val outcomes: List<RateOutcome>,
    @JsonInclude(JsonInclude.Include.NON_NULL)
    val trend: List<RateTrend>? = null
)