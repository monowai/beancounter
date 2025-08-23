package com.beancounter.marketdata.markets

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import java.time.LocalDate

class MarketCalendarTests {
    private val market = "TEST"
    private val christmasDay: LocalDate =
        LocalDate.of(
            2023,
            12,
            25
        )
    private val boxingDay: LocalDate =
        LocalDate.of(
            2023,
            12,
            26
        )
    private val markets = listOf(market)

    private val marketCalendarConfig =
        MarketCalendarConfig(
            listOf(
                MarketHolidayAnnual(
                    christmasDay.dayOfMonth,
                    christmasDay.monthValue.toString(),
                    "*",
                    markets
                ),
                MarketHolidayAnnual(
                    boxingDay.dayOfMonth,
                    boxingDay.monthValue.toString(),
                    "*",
                    markets
                )
            )
        )

    @Test
    fun `should handle NZ Christmas on Monday to Friday`() {
        val marketCalendar = MarketCalendar(marketCalendarConfig)
        assertThat(
            marketCalendar.isMarketHoliday(
                market,
                christmasDay
            )
        ).isTrue()
        assertThat(
            marketCalendar.isMarketHoliday(
                market,
                boxingDay
            )
        ).isTrue()
        assertThat(
            marketCalendar.getNextBusinessDay(
                christmasDay,
                marketCalendarConfig.marketHolidays(
                    2023,
                    market
                )
            )
        ).isEqualTo(
            LocalDate.of(
                2023,
                12,
                27
            )
        )
    }

    @Test
    @Disabled
    fun `should handle NZ Christmas on weekend`() {
        val marketCalendar = MarketCalendar(marketCalendarConfig)
        val christmasDay =
            LocalDate.of(
                2021,
                12,
                25
            )
        val boxingDay =
            LocalDate.of(
                2021,
                12,
                26
            )
        assertThat(
            marketCalendar.isMarketHoliday(
                market,
                christmasDay
            )
        ).isTrue()
        assertThat(
            marketCalendar.isMarketHoliday(
                market,
                boxingDay
            )
        ).isTrue()
        assertThat(
            marketCalendar.getNextBusinessDay(
                christmasDay,
                marketCalendarConfig.marketHolidays(
                    2021,
                    market
                )
            )
        ).isEqualTo(
            LocalDate.of(
                2021,
                12,
                29
            )
        )
    }
}