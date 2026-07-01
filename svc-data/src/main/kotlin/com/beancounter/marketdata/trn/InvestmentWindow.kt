package com.beancounter.marketdata.trn

import java.time.LocalDate

/**
 * Default trailing window (in days) for the Monthly Investment metric.
 * A rolling 30-day window replaces the former calendar-month boundary so the
 * figure reflects the last month of activity regardless of today's date.
 */
const val DEFAULT_INVESTMENT_WINDOW_DAYS = 30

/**
 * Resolve the rolling investment window: a trailing [days]-day span ending today.
 *
 * @param days trailing window length; values < 1 fall back to [DEFAULT_INVESTMENT_WINDOW_DAYS].
 * @param today window end (inclusive); defaults to the current date.
 * @return (startDate, endDate) where startDate = today - days and endDate = today.
 */
fun investmentWindow(
    days: Int,
    today: LocalDate = LocalDate.now()
): Pair<LocalDate, LocalDate> {
    val span = if (days < 1) DEFAULT_INVESTMENT_WINDOW_DAYS else days
    return today.minusDays(span.toLong()) to today
}