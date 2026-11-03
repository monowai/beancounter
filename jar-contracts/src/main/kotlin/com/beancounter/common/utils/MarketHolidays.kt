package com.beancounter.common.utils

import org.springframework.stereotype.Service
import java.time.LocalDate
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
        private val LONDON: ZoneId = ZoneId.of("Europe/London")
    }

    /**
     * Check if the given date is a US market holiday (back-compatible default).
     */
    fun isHoliday(date: LocalDate): Boolean = NyseHolidays.isHoliday(date)

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
            NyseHolidays.isHoliday(date)
        }

    /**
     * LSE bank holidays for [date]'s year, including weekend-substitute days.
     */
    fun isUkHoliday(date: LocalDate): Boolean = LseHolidays.isHoliday(date)
}