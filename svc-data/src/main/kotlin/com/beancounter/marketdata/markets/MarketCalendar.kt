package com.beancounter.marketdata.markets

import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate

data class MarketHolidayAnnual(
    val day: Int,
    val month: String,
    val year: String = "*",
    val markets: List<String>,
)

/**
 * Determine if it is a holiday on a market.
 */
@Service
class MarketCalendar(
    val marketCalendarConfig: MarketCalendarConfig,
) {
    fun isMarketHoliday(
        market: String,
        date: LocalDate,
    ): Boolean {
        val holidayMap =
            marketCalendarConfig.marketHolidays(
                date.year,
                market,
            )

        return isHoliday(
            date,
            holidayMap,
        ) ||
            isWeekend(date)
    }

    fun getNextBusinessDay(
        date: LocalDate,
        holidayList: List<LocalDate>,
    ): LocalDate {
        if (!isHoliday(
                date,
                holidayList,
            )
        ) {
            return date
        }

        var nextDay = date.plusDays(1)
        while (isHoliday(
                nextDay,
                holidayList,
            ) ||
            isWeekend(nextDay)
        ) {
            nextDay = nextDay.plusDays(1)
        }
        return nextDay
    }

    private fun isHoliday(
        date: LocalDate,
        holidayMap: List<LocalDate>,
    ): Boolean = holidayMap.contains(date)

    private fun isWeekend(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
}
