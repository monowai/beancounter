package com.beancounter.common.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * NYSE/NASDAQ holidays: fixed dates, their weekend observances, Good Friday and the
 * holidays fixed to the Nth (or last) weekday of a month.
 */
internal object NyseHolidays {
    private const val INDEPENDENCE_DAY = 4
    private const val JUNETEENTH_DAY = 19
    private const val JUNETEENTH_FIRST_YEAR = 2021
    private const val CHRISTMAS_DAY = 25
    private const val FIRST_OCCURRENCE = 1
    private const val THIRD_OCCURRENCE = 3
    private const val FOURTH_OCCURRENCE = 4
    private const val LAST_OCCURRENCE = -1

    // Holidays fixed to the Nth (or last) weekday of a month. Good Friday is computed apart.
    private val FLOATING_HOLIDAYS =
        listOf(
            Triple(Month.JANUARY, DayOfWeek.MONDAY, THIRD_OCCURRENCE), // MLK Day
            Triple(Month.FEBRUARY, DayOfWeek.MONDAY, THIRD_OCCURRENCE), // Presidents Day
            Triple(Month.MAY, DayOfWeek.MONDAY, LAST_OCCURRENCE), // Memorial Day
            Triple(Month.SEPTEMBER, DayOfWeek.MONDAY, FIRST_OCCURRENCE), // Labor Day
            Triple(Month.NOVEMBER, DayOfWeek.THURSDAY, FOURTH_OCCURRENCE) // Thanksgiving
        )
    private val DAYS_IN_WEEK = DayOfWeek.entries.size

    fun isHoliday(date: LocalDate): Boolean =
        isFixedHoliday(date) ||
            isObservedHoliday(date) ||
            isFloatingHoliday(date)

    /**
     * Fixed date holidays (always on the same date).
     */
    private fun isFixedHoliday(date: LocalDate): Boolean {
        val month = date.month
        val day = date.dayOfMonth

        return when ( // New Year's Day - January 1
            month
        ) {
            Month.JANUARY if day == 1 -> true
            // Juneteenth - June 19 (since 2021)
            Month.JUNE if day == JUNETEENTH_DAY && date.year >= JUNETEENTH_FIRST_YEAR -> true
            // Independence Day - July 4
            Month.JULY if day == INDEPENDENCE_DAY -> true
            // Christmas Day - December 25
            Month.DECEMBER if day == CHRISTMAS_DAY -> true
            else -> false
        }
    }

    /**
     * Observed holidays - when a fixed holiday falls on a weekend,
     * markets close on the nearest weekday.
     */
    private fun isObservedHoliday(date: LocalDate): Boolean =
        when (date.dayOfWeek) {
            // Friday observation of Saturday holiday
            DayOfWeek.FRIDAY -> isFixedHoliday(date.plusDays(1))
            // Monday observation of Sunday holiday
            DayOfWeek.MONDAY -> isFixedHoliday(date.minusDays(1))
            else -> false
        }

    /**
     * Floating holidays that occur on specific weekdays.
     */
    private fun isFloatingHoliday(date: LocalDate): Boolean =
        isGoodFriday(date) ||
            FLOATING_HOLIDAYS.any { (month, dayOfWeek, occurrence) ->
                date.month == month &&
                    date.dayOfWeek == dayOfWeek &&
                    if (occurrence == LAST_OCCURRENCE) {
                        isLastWeekdayOfMonth(date)
                    } else {
                        isNthWeekdayOfMonth(date, occurrence)
                    }
            }

    /**
     * Check if date is the Nth occurrence of its weekday in the month.
     */
    private fun isNthWeekdayOfMonth(
        date: LocalDate,
        n: Int
    ): Boolean {
        val dayOfMonth = date.dayOfMonth
        // The Nth occurrence falls between days (n-1)*7+1 and n*7
        return dayOfMonth > (n - 1) * DAYS_IN_WEEK && dayOfMonth <= n * DAYS_IN_WEEK
    }

    /**
     * Check if date is the last occurrence of its weekday in the month.
     */
    private fun isLastWeekdayOfMonth(date: LocalDate): Boolean {
        // If adding 7 days goes to next month, this is the last occurrence
        return date.plusWeeks(1).month != date.month
    }

    /**
     * Calculate if date is Good Friday (Friday before Easter Sunday).
     * Easter is the first Sunday after the first full moon on or after March 21.
     */
    private fun isGoodFriday(date: LocalDate): Boolean {
        if (date.dayOfWeek != DayOfWeek.FRIDAY) return false
        if (date.month != Month.MARCH && date.month != Month.APRIL) return false

        val easter = easterSunday(date.year)
        val goodFriday = easter.minusDays(2)

        return date == goodFriday
    }
}