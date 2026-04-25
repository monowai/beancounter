package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PricePoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Rebases a chronological list of [PricePoint]s onto the most recent
 * post-split share basis.
 *
 * Two sources of split metadata are honoured:
 *  1. The `split` column on individual price rows (set by the price-refresh
 *     enricher when a Global Quote lands on the ex-date).
 *  2. An explicit list of [SplitEvent]s. This covers the case where the
 *     historical backfill never captured a split (Alpha Vantage's
 *     TIME_SERIES_DAILY does not carry split coefficients), so the database
 *     has nothing but raw pre-split closes for the days leading up to a
 *     known corporate action.
 *
 * Algorithm:
 *  - Build a unified set of ex-dates from the column transitions
 *    (split=1 → split!=1 collapses sticky stamps so each event counts once)
 *    and any externally-supplied events.
 *  - For each price row, divide every OHLC + previousClose value by the
 *    product of every event factor whose date strictly follows the row.
 *  - Normalise the `split` column on the response so only the canonical
 *    ex-date row carries a non-1 marker — sticky neighbours go back to 1
 *    and rows without an ex-date stamp keep 1.
 */
object SplitAdjuster {
    private const val SCALE = 6
    private val ROUNDING = RoundingMode.HALF_UP

    data class SplitEvent(
        val date: LocalDate,
        val factor: BigDecimal
    )

    fun adjust(
        prices: List<PricePoint>,
        events: List<SplitEvent> = emptyList()
    ): List<PricePoint> {
        if (prices.isEmpty()) return prices

        val merged = mutableMapOf<LocalDate, BigDecimal>()

        for (ev in events) {
            if (ev.factor.compareTo(BigDecimal.ONE) != 0 && ev.factor.signum() > 0) {
                merged.putIfAbsent(ev.date, ev.factor)
            }
        }
        for (i in prices.indices) {
            val cur = prices[i].split
            val prev = if (i > 0) prices[i - 1].split else BigDecimal.ONE
            val curIsSplit = cur.compareTo(BigDecimal.ONE) != 0 && cur.signum() > 0
            val prevIsFlat = prev.compareTo(BigDecimal.ONE) == 0
            if (curIsSplit && prevIsFlat) {
                merged.putIfAbsent(prices[i].priceDate, cur)
            }
        }

        if (merged.isEmpty()) return prices

        val sortedEvents = merged.entries.sortedBy { it.key }

        return prices.map { p ->
            var factor = BigDecimal.ONE
            for ((date, eventFactor) in sortedEvents) {
                if (date.isAfter(p.priceDate)) {
                    factor = factor.multiply(eventFactor)
                }
            }
            val canonicalSplit = merged[p.priceDate] ?: BigDecimal.ONE
            if (factor.compareTo(BigDecimal.ONE) == 0 && canonicalSplit == p.split) {
                p
            } else {
                p.copy(
                    close = divideIfPositive(p.close, factor),
                    open = divideIfPositive(p.open, factor),
                    high = divideIfPositive(p.high, factor),
                    low = divideIfPositive(p.low, factor),
                    previousClose = divideIfPositive(p.previousClose, factor),
                    split = canonicalSplit
                )
            }
        }
    }

    private fun divideIfPositive(
        value: BigDecimal,
        factor: BigDecimal
    ): BigDecimal =
        if (factor.compareTo(BigDecimal.ONE) == 0 || value.signum() == 0) {
            value
        } else {
            value.divide(factor, SCALE, ROUNDING)
        }
}