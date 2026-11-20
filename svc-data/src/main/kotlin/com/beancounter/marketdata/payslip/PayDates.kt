package com.beancounter.marketdata.payslip

import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly pay dates. A payslip falls on its day of month, or the month's last day when the
 * month is shorter; the day of month is kept so a clamped February does not move March.
 */
object PayDates {
    private const val LONGEST_MONTH_DAYS = 31

    /** Valid days of month for a pay day. */
    val DAYS = 1..LONGEST_MONTH_DAYS

    /** True when [date] is the pay date for [dayOfMonth] in its month. */
    fun fallsOn(
        date: LocalDate,
        dayOfMonth: Int
    ): Boolean = date.dayOfMonth == minOf(dayOfMonth, date.lengthOfMonth())

    /** The pay date in the month after [payDate], on [dayOfMonth] clamped to that month's length. */
    fun next(
        payDate: LocalDate,
        dayOfMonth: Int
    ): LocalDate {
        val month = YearMonth.from(payDate).plusMonths(1)
        return month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
    }
}