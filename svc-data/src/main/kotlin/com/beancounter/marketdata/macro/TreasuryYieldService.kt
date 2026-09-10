package com.beancounter.marketdata.macro

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.abs

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
        val lookbackPoint = nearest(sorted, latest.date.minusDays(lookbackDays.toLong())) ?: return null
        val sevenDay = nearest(sorted, latest.date.minusDays(SEVEN_DAYS))
        val thirtyDay = nearest(sorted, latest.date.minusDays(THIRTY_DAYS))

        val chartPoints =
            listOfNotNull(thirtyDay, sevenDay, lookbackPoint, latest)
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

    private fun nearest(
        points: List<YieldPoint>,
        target: LocalDate
    ): YieldPoint? = points.minByOrNull { abs(ChronoUnit.DAYS.between(it.date, target)) }

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