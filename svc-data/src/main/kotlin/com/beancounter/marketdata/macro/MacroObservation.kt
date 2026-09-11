package com.beancounter.marketdata.macro

import com.beancounter.common.utils.KeyGenUtils
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * A single point-in-time sample of a macro indicator that has no queryable upstream history of
 * its own — currently Kalshi Fed-decision outcome probabilities, taken by
 * [MacroRefreshSchedule.refresh] via [RateExpectationsService.snapshot].
 *
 * [series] + [metric] together identify the time series (e.g. series `KALSHI:KXFEDDECISION-26SEP`,
 * metric the outcome label `Hike 25bps`); [observedAt] orders the samples so a trend delta can be
 * computed against "nearest observation ≥N days old".
 */
@Entity
@Table(name = "macro_observation")
data class MacroObservation(
    @Column(nullable = false, length = 64)
    var series: String = "",
    @Column(nullable = false, length = 64)
    var metric: String = "",
    @Column(nullable = false, precision = 12, scale = 6)
    var value: BigDecimal = BigDecimal.ZERO,
    // MIN is a JPA no-arg-constructor placeholder, never read — callers always pass a
    // dateUtils-zoned timestamp (ZoneLeakGuardTest forbids default-zone now() here).
    @Column(name = "observed_at", nullable = false)
    var observedAt: LocalDateTime = LocalDateTime.MIN
) {
    @Id
    val id: String = KeyGenUtils().id
}