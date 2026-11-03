package com.beancounter.common

import com.beancounter.common.utils.MarketHolidays
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * US observed holidays: a fixed holiday on a Saturday closes the Friday
 * before, one on a Sunday closes the Monday after, and no other weekday moves.
 */
internal class MarketHolidaysUsObservedTest {
    private val holidays = MarketHolidays()

    @Test
    fun `should close the friday before a saturday holiday`() {
        // Independence Day 2026 is a Saturday.
        assertThat(holidays.isHoliday(LocalDate.of(2026, 7, 3))).isTrue()
    }

    @Test
    fun `should close the monday after a sunday holiday`() {
        // Christmas Day 2022 is a Sunday.
        assertThat(holidays.isHoliday(LocalDate.of(2022, 12, 26))).isTrue()
    }

    @Test
    fun `should not move a weekend holiday to any other weekday`() {
        // Thursday before the Saturday holiday, Tuesday after the Sunday one.
        assertThat(holidays.isHoliday(LocalDate.of(2026, 7, 2))).isFalse()
        assertThat(holidays.isHoliday(LocalDate.of(2022, 12, 27))).isFalse()
    }

    @Test
    fun `should leave fridays and mondays without an adjacent holiday open`() {
        assertThat(holidays.isHoliday(LocalDate.of(2026, 7, 10))).isFalse()
        assertThat(holidays.isHoliday(LocalDate.of(2022, 12, 19))).isFalse()
    }
}