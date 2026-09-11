package com.beancounter.marketdata.macro

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

/**
 * Projects AlphaVantage TREASURY_YIELD data (via [TreasuryYieldFetcher]) into the sparse
 * chart-ready shape `/macro/indicators` returns.
 *
 * Maturities are fetched independently and a maturity with no upstream data is simply omitted
 * from the result — [MacroIndicatorsService] must never 500 for partial provider coverage.
 */
@Service
class TreasuryYieldService(
    private val fetcher: TreasuryYieldFetcher
) {
    fun getYields(lookbackDays: Int = DEFAULT_LOOKBACK_DAYS): List<YieldSeries> =
        MATURITIES.mapNotNull { (series, maturity) -> project(series, fetcher.fetch(maturity), lookbackDays) }

    private fun project(
        series: String,
        points: List<YieldPoint>,
        lookbackDays: Int
    ): YieldSeries? {
        if (points.isEmpty()) return null
        val sorted = points.sortedByDescending { it.date }
        val latest = sorted.first()
        val lookbackPoint = nearest(sorted, latest.date.minusDays(lookbackDays.toLong()))
        val sevenDay = nearest(sorted, latest.date.minusDays(SEVEN_DAYS))
        val thirtyDay = nearest(sorted, latest.date.minusDays(THIRTY_DAYS))

        val chartPoints =
            listOf(thirtyDay, sevenDay, lookbackPoint, latest)
                .distinctBy { it.date }
                .sortedBy { it.date }

        return YieldSeries(
            series = series,
            latest = latest.value,
            latestDate = latest.date,
            lookback = lookbackPoint.value,
            lookbackDate = lookbackPoint.date,
            changeBps =
                latest.value
                    .subtract(
                        lookbackPoint.value
                    ).multiply(BPS_MULTIPLIER)
                    .setScale(2, RoundingMode.HALF_UP),
            points = chartPoints
        )
    }

    /**
     * The latest point at-or-before [target] — never a point AFTER it, which would put the
     * "lookback" point on the wrong side of the window and corrupt [YieldSeries.changeBps]'s sign
     * (e.g. a weekend/holiday gap letting the nearest-by-distance point land after the target).
     * Falls back to the earliest available point only when nothing precedes [target] at all (e.g. a
     * maturity whose history doesn't yet reach that far back) — [points] is always non-empty at
     * every call site ([project] returns early on an empty list), so this never returns null and
     * callers don't need an `?: return null`/nullable chain for it.
     */
    private fun nearest(
        points: List<YieldPoint>,
        target: LocalDate
    ): YieldPoint {
        require(points.isNotEmpty()) { "nearest requires a non-empty point list" }
        return points.filter { !it.date.isAfter(target) }.maxByOrNull { it.date }
            ?: points.sortedBy { it.date }.first()
    }

    companion object {
        const val DEFAULT_LOOKBACK_DAYS = 14
        private const val SEVEN_DAYS = 7L
        private const val THIRTY_DAYS = 30L
        private val BPS_MULTIPLIER = BigDecimal(100)

        // AlphaVantage TREASURY_YIELD maturity codes -> BC series labels.
        private val MATURITIES =
            listOf(
                "US10Y" to "10year",
                "US2Y" to "2year"
            )
    }
}