package com.beancounter.marketdata.payslip

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * A recurring payslip falls on its day of month, or the month's last day when the month is
 * shorter. A short month does not move later pay dates: the day of month is kept separately.
 */
class PayDatesTest {
    @Test
    fun `should fall on the same day next month`() {
        assertThat(PayDates.next(LocalDate.of(2026, 11, 25), 25)).isEqualTo(LocalDate.of(2026, 12, 25))
    }

    @Test
    fun `should clamp 31 January to 28 February in a non-leap year`() {
        assertThat(PayDates.next(LocalDate.of(2027, 1, 31), 31)).isEqualTo(LocalDate.of(2027, 2, 28))
    }

    @Test
    fun `should clamp 31 January to 29 February in a leap year`() {
        assertThat(PayDates.next(LocalDate.of(2028, 1, 31), 31)).isEqualTo(LocalDate.of(2028, 2, 29))
    }

    @Test
    fun `should restore day 31 in March after a clamped February`() {
        assertThat(PayDates.next(LocalDate.of(2028, 2, 29), 31)).isEqualTo(LocalDate.of(2028, 3, 31))
        assertThat(PayDates.next(LocalDate.of(2027, 2, 28), 31)).isEqualTo(LocalDate.of(2027, 3, 31))
    }

    @Test
    fun `should roll December into January of the next year`() {
        assertThat(PayDates.next(LocalDate.of(2026, 12, 31), 31)).isEqualTo(LocalDate.of(2027, 1, 31))
    }
}