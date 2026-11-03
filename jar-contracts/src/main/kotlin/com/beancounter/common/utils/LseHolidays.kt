package com.beancounter.common.utils

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month

/**
 * LSE (England & Wales) bank holidays, including weekend-substitute days.
 */
internal object LseHolidays {
    private const val BOXING_DAY = 26

    fun isHoliday(date: LocalDate): Boolean = holidays(date.year).contains(date)

    private fun holidays(year: Int): Set<LocalDate> {
        val easter = easterSunday(year)
        val holidays = sortedSetOf<LocalDate>()

        // Fixed weekday holidays (Easter-relative + bank holiday Mondays) —
        // these always fall on a weekday so need no substitution.
        holidays.add(easter.minusDays(2)) // Good Friday
        holidays.add(easter.plusDays(1)) // Easter Monday
        holidays.add(firstMondayOf(year, Month.MAY)) // Early May bank holiday
        holidays.add(lastMondayOf(year, Month.MAY)) // Spring bank holiday
        holidays.add(lastMondayOf(year, Month.AUGUST)) // Summer bank holiday

        // Fixed-date holidays that shift to a substitute weekday on weekends.
        addObserved(holidays, LocalDate.of(year, Month.JANUARY, 1)) // New Year
        addObserved(holidays, LocalDate.of(year, Month.DECEMBER, CHRISTMAS_DAY))
        addObserved(holidays, LocalDate.of(year, Month.DECEMBER, BOXING_DAY))

        return holidays
    }

    /**
     * Adds [holiday] to [set], substituting weekend dates (and resolving
     * collisions, e.g. Christmas & Boxing Day both falling on a weekend) to
     * the next free weekday.
     */
    private fun addObserved(
        set: MutableSet<LocalDate>,
        holiday: LocalDate
    ) {
        var observed = holiday
        while (set.contains(observed) ||
            observed.dayOfWeek == DayOfWeek.SATURDAY ||
            observed.dayOfWeek == DayOfWeek.SUNDAY
        ) {
            observed = observed.plusDays(1)
        }
        set.add(observed)
    }

    private fun firstMondayOf(
        year: Int,
        month: Month
    ): LocalDate {
        var date = LocalDate.of(year, month, 1)
        while (date.dayOfWeek != DayOfWeek.MONDAY) {
            date = date.plusDays(1)
        }
        return date
    }

    private fun lastMondayOf(
        year: Int,
        month: Month
    ): LocalDate {
        var date = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
        while (date.dayOfWeek != DayOfWeek.MONDAY) {
            date = date.minusDays(1)
        }
        return date
    }
}