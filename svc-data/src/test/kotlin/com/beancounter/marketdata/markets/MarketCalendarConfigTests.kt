package com.beancounter.marketdata.markets

import com.beancounter.marketdata.Constants.Companion.NZX
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import java.time.Month

@SpringBootTest
class MarketCalendarConfigTests {
    @Autowired
    lateinit var marketCalendarConfig: MarketCalendarConfig
    private val market = "MY"

    @Test
    fun configLoaded() {
        assertThat(marketCalendarConfig).isNotNull
        assertThat(marketCalendarConfig.values).isNotEmpty // System defaults
    }

    @Test
    fun marketHolidaysArePresentAndEmpty() {
        assertThat(
            marketCalendarConfig.marketHolidays(
                2014,
                NZX.code
            )
        ).isNotNull.isEmpty()
    }

    @Test
    fun marketHolidaysAreBuilt() {
        val markets = listOf(market)
        val day = 25
        val month = Month.DECEMBER
        val year = 2014
        val marketCalendar =
            MarketCalendarConfig(
                listOf(
                    MarketHolidayAnnual(
                        day,
                        Month.DECEMBER.value.toString(),
                        markets = markets
                    )
                )
            )
        val result =
            marketCalendar.buildMarketHolidays(
                year,
                market
            )
        assertThat(result).isNotEmpty.hasSize(1)
        assertThat(
            result[0]
        ).hasYear(year).hasMonth(month).hasDayOfMonth(day)
    }
}