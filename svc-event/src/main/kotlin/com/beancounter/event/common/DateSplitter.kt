package com.beancounter.event.common

import com.beancounter.common.utils.DateUtils
import org.springframework.stereotype.Service
import java.time.LocalDate

/**
 * Calculates an array of dates from start date to end date inclusive.
 * One date for each split days period is calculated.
 */
@Service
class DateSplitter(val dateUtils: DateUtils) {

    fun split(
        days: Int = 25,
        from: String = dateUtils.date.minusDays(days.toLong()).toString(),
        until: String = dateUtils.today(),
    ): List<LocalDate> {
        if (from.contentEquals(until)) {
            return listOf(dateUtils.getOrThrow(from))
        }

        var calculatedDate = dateUtils.getOrThrow(from)
        val endDate = dateUtils.getOrThrow(until)
        val results: MutableList<LocalDate> = mutableListOf()

        while (calculatedDate.isBefore(endDate)) {
            results.add(calculatedDate)
            calculatedDate = calculatedDate.plusDays(days.toLong())
        }
        if (calculatedDate >= endDate) {
            results.add(endDate)
        }

        return results
    }

    fun dateRange(date: String, toDate: String): List<LocalDate> {
        val asAt: String = if (date.equals(DateUtils.today, ignoreCase = true)) {
            dateUtils.today()
        } else {
            dateUtils.getDate(date).toString()
        }
        return split(from = asAt, until = toDate, days = 1)
    }
}
