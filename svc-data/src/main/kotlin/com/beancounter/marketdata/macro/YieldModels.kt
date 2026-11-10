package com.beancounter.marketdata.macro

import java.math.BigDecimal
import java.time.LocalDate

/**
 * One dated observation of a FRED series (a daily yield, or a monthly index or level).
 */
data class SeriesPoint(
    val date: LocalDate,
    val value: BigDecimal
)

/**
 * Projected treasury-yield series for one maturity (e.g. `US10Y`), returned by
 * [TreasuryYieldService.getYields].
 *
 * [points] is a sparse chart series — latest, nearest-lookback, nearest-7d, nearest-30d — not the
 * full daily history, to keep the `/macro/indicators` payload small.
 */
data class YieldSeries(
    val series: String,
    val latest: BigDecimal,
    val latestDate: LocalDate,
    val lookback: BigDecimal,
    val lookbackDate: LocalDate,
    val changeBps: BigDecimal,
    val points: List<SeriesPoint>
)