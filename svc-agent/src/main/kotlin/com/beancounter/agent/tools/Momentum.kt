package com.beancounter.agent.tools

import com.beancounter.common.contracts.PricePoint
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Trend and relative-strength readings off one asset's split-adjusted daily closes, all
 * measured from the latest close. Every reading is a ratio (0.12 = 12%), except [high52w] and
 * [low52w], which are the 52-week high and low closes themselves. Each is null when the series
 * is too short to support it, so the model never reads a guess as a reading.
 */
data class Momentum(
    val asOf: String? = null,
    val return1m: Double? = null,
    val return3m: Double? = null,
    val return6m: Double? = null,
    val return12m: Double? = null,
    val vsSma50: Double? = null,
    val vsSma200: Double? = null,
    val fromHigh52w: Double? = null,
    val fromLow52w: Double? = null,
    val high52w: Double? = null,
    val low52w: Double? = null
) {
    companion object {
        private const val SCALE = 4
        private const val SHORT_AVERAGE = 50
        private const val LONG_AVERAGE = 200

        // A year back from the latest close can land on a weekend or a holiday run, so a series
        // whose first close falls within this many days after it still spans the 52 weeks.
        private const val YEAR_EDGE_DAYS = 7L

        fun of(points: List<PricePoint>): Momentum {
            val series = points.filter { it.close.signum() > 0 }.sortedBy { it.priceDate }
            if (series.isEmpty()) return Momentum()
            val latest = series.last()
            val yearAgo = latest.priceDate.minusYears(1)
            // A 52-week high or low needs a year of history, give or take the non-trading days at its edge.
            val year =
                series
                    .filter { it.priceDate > yearAgo }
                    .takeIf { series.first().priceDate <= yearAgo.plusDays(YEAR_EDGE_DAYS) }
            val high = year?.maxOf { it.close }
            val low = year?.minOf { it.close }
            return Momentum(
                asOf = latest.priceDate.toString(),
                return1m = change(latest.close, closeOnOrBefore(series, latest.priceDate.minusMonths(1))),
                return3m = change(latest.close, closeOnOrBefore(series, latest.priceDate.minusMonths(3))),
                return6m = change(latest.close, closeOnOrBefore(series, latest.priceDate.minusMonths(6))),
                return12m = change(latest.close, closeOnOrBefore(series, latest.priceDate.minusMonths(12))),
                vsSma50 = change(latest.close, average(series, SHORT_AVERAGE)),
                vsSma200 = change(latest.close, average(series, LONG_AVERAGE)),
                fromHigh52w = change(latest.close, high),
                fromLow52w = change(latest.close, low),
                high52w = high?.toDouble(),
                low52w = low?.toDouble()
            )
        }

        private fun closeOnOrBefore(
            series: List<PricePoint>,
            date: LocalDate
        ): BigDecimal? = series.lastOrNull { it.priceDate <= date }?.close

        private fun average(
            series: List<PricePoint>,
            closes: Int
        ): BigDecimal? {
            if (series.size < closes) return null
            return series
                .takeLast(closes)
                .sumOf { it.close }
                .divide(BigDecimal(closes), SCALE * 2, RoundingMode.HALF_UP)
        }

        private fun change(
            latest: BigDecimal,
            base: BigDecimal?
        ): Double? =
            base?.let {
                latest
                    .divide(it, SCALE * 2, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE)
                    .setScale(SCALE, RoundingMode.HALF_UP)
                    .toDouble()
            }
    }
}