package com.beancounter.common.utils

import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Month
import java.time.ZoneId

/**
 * Determines whether a date is a market holiday.
 *
 * Routing is by exchange timezone:
 *  - `Europe/London` resolves to the LSE (England & Wales) bank-holiday
 *    calendar.
 *  - every other zone resolves to the US (NYSE/NASDAQ) calendar, which is
 *    also the back-compatible default for the zone-less [isHoliday] overload.
 */
@Service
class MarketHolidays {
    companion object {
        private const val JUNETEENTH_DAY = 19
        private const val JUNETEENTH_FIRST_YEAR = 2021
        private const val CHRISTMAS_DAY = 25
        private const val BOXING_DAY = 26
        private val LONDON: ZoneId = ZoneId.of("Europe/London")
    }

    /**
     * Check if the given date is a US market holiday (back-compatible default).
     */
    fun isHoliday(date: LocalDate): Boolean = isUsHoliday(date)

    /**
     * Check if the given date is a holiday for the market in [zone].
     * London markets use the UK calendar; all others fall back to the US one.
     */
    fun isHoliday(
        date: LocalDate,
        zone: ZoneId?
    ): Boolean =
        if (zone == LONDON) {
            isUkHoliday(date)
        } else {
            isUsHoliday(date)
        }

    private fun isUsHoliday(date: LocalDate): Boolean =
        isFixedHoliday(date) ||
            isObservedHoliday(date) ||
            isFloatingHoliday(date)

    /**
     * LSE bank holidays for [date]'s year, including weekend-substitute days.
     */
    fun isUkHoliday(date: LocalDate): Boolean = ukHolidays(date.year).contains(date)

    private fun ukHolidays(year: Int): Set<LocalDate> {
        val easterSunday = calculateEasterSunday(year)
        val holidays = sortedSetOf<LocalDate>()

        // Fixed weekday holidays (Easter-relative + bank holiday Mondays) —
        // these always fall on a weekday so need no substitution.
        holidays.add(easterSunday.minusDays(2)) // Good Friday
        holidays.add(easterSunday.plusDays(1)) // Easter Monday
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
            Month.JULY if day == 4 -> true
            // Christmas Day - December 25
            Month.DECEMBER if day == CHRISTMAS_DAY -> true
            else -> false
        }
    }

    /**
     * Observed holidays - when a fixed holiday falls on a weekend,
     * markets close on the nearest weekday.
     */
    private fun isObservedHoliday(date: LocalDate): Boolean {
        val dayOfWeek = date.dayOfWeek

        // Friday observation of Saturday holiday
        if (dayOfWeek == DayOfWeek.FRIDAY) {
            val saturday = date.plusDays(1)
            if (isFixedHoliday(saturday)) return true
        }

        // Monday observation of Sunday holiday
        if (dayOfWeek == DayOfWeek.MONDAY) {
            val sunday = date.minusDays(1)
            if (isFixedHoliday(sunday)) return true
        }

        return false
    }

    /**
     * Floating holidays that occur on specific weekdays.
     */
    private fun isFloatingHoliday(date: LocalDate): Boolean {
        val month = date.month
        val dayOfWeek = date.dayOfWeek
        date.dayOfMonth

        return when {
            // MLK Day - 3rd Monday in January
            month == Month.JANUARY &&
                dayOfWeek == DayOfWeek.MONDAY &&
                isNthWeekdayOfMonth(date, 3) -> true
            // Presidents Day - 3rd Monday in February
            month == Month.FEBRUARY &&
                dayOfWeek == DayOfWeek.MONDAY &&
                isNthWeekdayOfMonth(date, 3) -> true
            // Good Friday - Friday before Easter (complex calculation)
            isGoodFriday(date) -> true
            // Memorial Day - Last Monday in May
            month == Month.MAY &&
                dayOfWeek == DayOfWeek.MONDAY &&
                isLastWeekdayOfMonth(date) -> true
            // Labor Day - 1st Monday in September
            month == Month.SEPTEMBER &&
                dayOfWeek == DayOfWeek.MONDAY &&
                isNthWeekdayOfMonth(date, 1) -> true
            // Thanksgiving - 4th Thursday in November
            month == Month.NOVEMBER &&
                dayOfWeek == DayOfWeek.THURSDAY &&
                isNthWeekdayOfMonth(date, 4) -> true
            else -> false
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
        return dayOfMonth > (n - 1) * 7 && dayOfMonth <= n * 7
    }

    /**
     * Check if date is the last occurrence of its weekday in the month.
     */
    private fun isLastWeekdayOfMonth(date: LocalDate): Boolean {
        // If adding 7 days goes to next month, this is the last occurrence
        return date.plusDays(7).month != date.month
    }

    /**
     * Calculate if date is Good Friday (Friday before Easter Sunday).
     * Easter is the first Sunday after the first full moon on or after March 21.
     */
    private fun isGoodFriday(date: LocalDate): Boolean {
        if (date.dayOfWeek != DayOfWeek.FRIDAY) return false
        if (date.month != Month.MARCH && date.month != Month.APRIL) return false

        val easterSunday = calculateEasterSunday(date.year)
        val goodFriday = easterSunday.minusDays(2)

        return date == goodFriday
    }

    /**
     * Calculate Easter Sunday using the Anonymous Gregorian algorithm.
     */
    private fun calculateEasterSunday(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1

        return LocalDate.of(year, month, day)
    }
}