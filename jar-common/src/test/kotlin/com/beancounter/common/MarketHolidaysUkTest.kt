package com.beancounter.common

import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MarketHolidays
import com.beancounter.common.utils.PreviousClosePriceDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * UK (LSE) bank-holiday calendar and its market-scoped routing through the
 * trading-day walk-back. London markets observe UK holidays; US holidays must
 * NOT close the LSE, and vice-versa.
 */
internal class MarketHolidaysUkTest {
    private val holidays = MarketHolidays()
    private val previousClose = PreviousClosePriceDate(DateUtils("Asia/Singapore"))
    private val london: ZoneId = ZoneId.of("Europe/London")

    private val lon =
        Market(
            code = "LON",
            timezoneId = "Europe/London"
        )
    private val nasdaq = Market("NASDAQ") // US/Eastern

    @Test
    fun `uk bank holidays are recognised`() {
        // Boxing Day (US has none), Early May & Summer bank holidays (UK-only).
        assertThat(holidays.isHoliday(LocalDate.of(2024, 12, 26), london)).isTrue()
        assertThat(holidays.isHoliday(LocalDate.of(2024, 5, 6), london)).isTrue() // 1st Mon May
        assertThat(holidays.isHoliday(LocalDate.of(2024, 8, 26), london)).isTrue() // last Mon Aug
        assertThat(holidays.isHoliday(LocalDate.of(2024, 3, 29), london)).isTrue() // Good Friday
    }

    @Test
    fun `uk weekend christmas substitutes to following weekdays`() {
        // 2021-12-25 Sat → observed Mon 27; 2021-12-26 Sun → observed Tue 28.
        assertThat(holidays.isHoliday(LocalDate.of(2021, 12, 27), london)).isTrue()
        assertThat(holidays.isHoliday(LocalDate.of(2021, 12, 28), london)).isTrue()
    }

    @Test
    fun `us-only holidays do not close the LSE`() {
        // US Independence Day & Thanksgiving — LSE trades.
        assertThat(holidays.isHoliday(LocalDate.of(2024, 7, 4), london)).isFalse()
        assertThat(holidays.isHoliday(LocalDate.of(2024, 11, 28), london)).isFalse()
    }

    @Test
    fun `uk-only holidays do not close US markets`() {
        // Boxing Day & Summer bank holiday — NYSE/NASDAQ trade.
        assertThat(holidays.isHoliday(LocalDate.of(2024, 12, 26), ZoneId.of("US/Eastern"))).isFalse()
        assertThat(holidays.isHoliday(LocalDate.of(2024, 8, 26), ZoneId.of("US/Eastern"))).isFalse()
    }

    @Test
    fun `trading-day walk-back honours the market calendar`() {
        // Boxing Day 2024-12-26 (Thu) is closed for LON, open for NASDAQ.
        val boxingDay = ZonedDateTime.of(2024, 12, 26, 12, 0, 0, 0, london)
        assertThat(previousClose.isTradingDay(boxingDay, lon)).isFalse()
        assertThat(previousClose.isTradingDay(boxingDay, nasdaq)).isTrue()

        // US Independence Day is open for LON, closed for NASDAQ.
        val july4 = ZonedDateTime.of(2024, 7, 4, 12, 0, 0, 0, ZoneId.of("America/New_York"))
        assertThat(previousClose.isTradingDay(july4, lon)).isTrue()
        assertThat(previousClose.isTradingDay(july4, nasdaq)).isFalse()
    }
}