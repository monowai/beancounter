package com.beancounter.marketdata.providers

import com.beancounter.common.contracts.PricePoint
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Rebases a chronological list of [PricePoint]s onto the most recent
 * post-split share basis.
 *
 * Provider data carries the split coefficient on the ex-date row (and
 * occasionally on a sticky neighbour — Alpha Vantage's ±1-day enricher
 * fallback was a known source of this). Pre-event rows store raw
 * pre-split prices; ex-date and post-event rows store adjusted prices.
 *
 * The frontend used to perform this rebasing itself, which left the
 * adjustment scattered across each consumer and at risk of double-
 * dividing when stale stamps lingered on more than one row. Doing it
 * once here means every caller gets clean, charting-ready data.
 *
 * Algorithm:
 *  - Walk forward and identify ex-dates as rows where `split != 1` and
 *    the previous row's `split == 1`. Consecutive non-1 rows belong to
 *    the same event (sticky stamps) so only the first counts.
 *  - For each ex-date event, divide every OHLC + previousClose value
 *    on every row strictly before the event index by the event's split
 *    factor. Multiple events compound.
 *  - Normalise the `split` column so only the canonical ex-date keeps
 *    its non-1 value; sticky neighbours are reset to 1 to give the
 *    chart a single split marker.
 */
object SplitAdjuster {
    private const val SCALE = 6
    private val ROUNDING = RoundingMode.HALF_UP

    fun adjust(prices: List<PricePoint>): List<PricePoint> {
        if (prices.isEmpty()) return prices

        data class SplitEvent(
            val firstIdx: Int,
            val factor: BigDecimal
        )

        val events = mutableListOf<SplitEvent>()
        for (i in prices.indices) {
            val cur = prices[i].split
            val prev = if (i > 0) prices[i - 1].split else BigDecimal.ONE
            val curIsSplit = cur.compareTo(BigDecimal.ONE) != 0 && cur.signum() > 0
            val prevIsFlat = prev.compareTo(BigDecimal.ONE) == 0
            if (curIsSplit && prevIsFlat) {
                events.add(SplitEvent(i, cur))
            }
        }
        if (events.isEmpty()) return prices

        return prices.mapIndexed { i, p ->
            var factor = BigDecimal.ONE
            for (ev in events) {
                if (i < ev.firstIdx) factor = factor.multiply(ev.factor)
            }
            val normalisedSplit =
                if (events.any { it.firstIdx == i }) {
                    p.split
                } else {
                    BigDecimal.ONE
                }
            if (factor.compareTo(BigDecimal.ONE) == 0 && normalisedSplit == p.split) {
                p
            } else {
                p.copy(
                    close = divideIfPositive(p.close, factor),
                    open = divideIfPositive(p.open, factor),
                    high = divideIfPositive(p.high, factor),
                    low = divideIfPositive(p.low, factor),
                    previousClose = divideIfPositive(p.previousClose, factor),
                    split = normalisedSplit
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