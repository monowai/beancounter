package com.beancounter.marketdata.providers

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Internal-hole detection for a stored daily price series.
 *
 * Min/max coverage checks cannot see a series that is complete at both edges but
 * empty in the middle — an asset not held for a few months, say, whose daily
 * refresh stopped and restarted. The row after such a hole derives its
 * `previousClose` from the row before it, so a "daily" change quietly spans the
 * whole hole (US.TIP read -2.02% for a +0.19% day, 2026-10-06).
 */
object PriceSeriesGaps {
    /**
     * Widest calendar stretch between two consecutive stored rows that still counts
     * as routine: weekends, a four-day holiday weekend, and a long market closure
     * such as Golden Week or Chinese New Year all fit inside it.
     */
    const val MAX_CALENDAR_GAP_DAYS = 10L

    /**
     * The last stored date before the first hole wider than [MAX_CALENDAR_GAP_DAYS],
     * or null when the series has no such hole. Backfilling from that date refills
     * the hole; the dedup in `PriceService.handle` drops the one row that exists.
     *
     * Sorts its input: the repository query is ordered, but the history controller
     * passes dates taken from a split-adjusted series, and the cost is trivial.
     */
    fun firstGapStart(dates: List<LocalDate>): LocalDate? {
        val sorted = dates.sorted()
        return sorted
            .zipWithNext()
            .firstOrNull { (prior, next) -> ChronoUnit.DAYS.between(prior, next) > MAX_CALENDAR_GAP_DAYS }
            ?.first
    }
}