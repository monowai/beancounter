package com.beancounter.marketdata.payslip

import java.time.LocalDate
import java.time.YearMonth

/**
 * Monthly pay dates. A payslip falls on its day of month, or the month's last day when the
 * month is shorter; the day of month is kept so a clamped February does not move March.
 */
object PayDates {
    /** The pay date in the month after [payDate], on [dayOfMonth] clamped to that month's length. */
    fun next(
        payDate: LocalDate,
        dayOfMonth: Int
    ): LocalDate {
        val month = YearMonth.from(payDate).plusMonths(1)
        return month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
    }
}