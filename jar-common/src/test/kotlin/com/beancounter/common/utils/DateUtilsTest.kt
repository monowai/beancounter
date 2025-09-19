package com.beancounter.common.utils

import com.beancounter.common.TestHelpers
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

/**
 * Test class for DateUtils. This class comprehensively tests the functionality of the DateUtils class,
 * in the default timezone.
 * It ensures that the methods behave as expected under various conditions, including handling valid and
 * invalid date inputs, recognizing 'today' correctly, and matching time zones and formatted outputs.
 *
 * The class tests several key functionalities:
 * - The ability of the isToday method to correctly determine if a given date string represents the current date.
 * - The correctness of time zone handling, ensuring that the configured time zone is respected in all date-time calculations.
 * - The functionality of handling dates across different time zones, specifically testing if the dates are processed
 * - The robustness of date parsing and formatting, verifying that invalid date strings are appropriately caught and
 *   handled via exceptions, and that valid date strings are processed without errors.
 * - The consistency of date string outputs, ensuring that methods return consistent and expected outputs for 'today'
 *   and other specific date inputs across various methods like offsetDateString and getFormattedDate.
 *
 * Each test method is designed to focus on specific behaviors of the DateUtils class, making sure that each aspect
 * of its functionality is correctly implemented and behaves as expected in both normal and edge cases.
 */

internal class DateUtilsTest {
    private lateinit var dateUtils: DateUtils

    @BeforeEach
    fun setUp() {
        dateUtils = DateUtils()
    }

    @Test
    fun `should recognize today correctly`() {
        with(dateUtils) {
            assertThat(isToday(dateUtils.today())).isTrue()
            assertThat(isToday()).isTrue()
            assertThat(isToday("")).isTrue()
            assertThat(isToday(" ")).isTrue()
            assertThat(isToday("ToDay")).isTrue()
        }
    }

    @Test
    fun `should process valid dates without exceptions`() {
        assertThat(dateUtils.getDate()).isNotNull()
        assertThat(dateUtils.getDate("2020-11-11")).isNotNull()
    }

    @Test
    fun `should throw BusinessException for invalid date strings`() {
        TestHelpers.assertThrowsWithMessage<BusinessException>("Unable to parse the date 2019-11-07'") {
            dateUtils.getDate("2019-11-07'")
        }
    }

    @Test
    fun `should match offset date string with formatted date for today`() {
        val default = dateUtils.offsetDateString()
        assertThat(dateUtils.getFormattedDate()).isEqualTo(default)
        assertThat(dateUtils.getFormattedDate("today")).isEqualTo(default)
    }

    @Test
    fun `should match formatted date with local date for specific and today inputs`() {
        val default = dateUtils.date
        with(dateUtils) {
            assertThat(getFormattedDate()).isEqualTo(default)
            assertThat(getFormattedDate("today")).isEqualTo(default)
            assertThat(getFormattedDate("ToDay")).isEqualTo(default)
        }
    }

    @Test
    fun `should throw BusinessException for non-date string input`() {
        TestHelpers.assertThrowsWithMessage<BusinessException>("Unable to parse the date ABC-MM-11") {
            dateUtils.getDate("ABC-MM-11")
        }
    }

    @Test
    fun `should parse miscellaneous date formats correctly`() {
        val dateUtils = DateUtils()
        assertThat(dateUtils.getDate("2021-01-01")).isNotNull()
        assertThat(dateUtils.getDate("2021-08-8")).isNotNull()
        assertThat(dateUtils.getDate("2021-8-08")).isNotNull()
        assertThat(dateUtils.getDate("2021-8-8")).isNotNull()
    }

    @Test
    fun `should return zero for same date input in calculateYearFraction`() {
        val date = LocalDate.of(2023, 6, 15)
        val result = dateUtils.calculateYearFraction(date, date)
        assertThat(result).isEqualTo(0.0)
    }

    @Test
    fun `should calculate year fraction for same-year non-leap year period`() {
        // 6 months in 2023 (non-leap year)
        val startDate = LocalDate.of(2023, 1, 1)
        val endDate = LocalDate.of(2023, 7, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Expected: 181 days / 365 days = 0.4958...
        assertThat(result).isCloseTo(
            0.4959,
            org.assertj.core.data.Offset
                .offset(0.0001)
        )
    }

    @Test
    fun `should calculate year fraction for same-year leap year period`() {
        // 6 months in 2024 (leap year)
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 7, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Expected: 182 days / 366 days = 0.4972...
        assertThat(result).isCloseTo(
            0.4973,
            org.assertj.core.data.Offset
                .offset(0.0001)
        )
    }

    @Test
    fun `should calculate year fraction for exact one year non-leap to non-leap`() {
        val startDate = LocalDate.of(2023, 1, 1)
        val endDate = LocalDate.of(2024, 1, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // ACTUAL/ACTUAL convention: 365 days across year boundary using weighted average
        // Expected around 0.999 due to 365.25 average for multi-year calculation
        assertThat(result).isCloseTo(
            0.999,
            org.assertj.core.data.Offset
                .offset(0.001)
        )
    }

    @Test
    fun `should calculate year fraction for exact one leap year`() {
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2025, 1, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // ACTUAL/ACTUAL convention: 366 days across year boundary using weighted average
        // Expected around 1.002 due to leap year having more days than 365.25 average
        assertThat(result).isCloseTo(
            1.002,
            org.assertj.core.data.Offset
                .offset(0.001)
        )
    }

    @Test
    fun `should calculate year fraction spanning leap year boundary`() {
        // From end of 2023 to end of 2024 (spans leap year)
        val startDate = LocalDate.of(2023, 12, 31)
        val endDate = LocalDate.of(2024, 12, 31)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Should be close to 1.0 year but account for leap year precision
        assertThat(result).isCloseTo(
            1.0,
            org.assertj.core.data.Offset
                .offset(0.003)
        )
    }

    @Test
    fun `should calculate year fraction for multi-year period`() {
        // 2.5 years spanning multiple years including leap year
        val startDate = LocalDate.of(2023, 6, 1)
        val endDate = LocalDate.of(2026, 1, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Expected approximately 2.58 years
        assertThat(result).isGreaterThan(2.5)
        assertThat(result).isLessThan(2.7)
    }

    @Test
    fun `should handle February 29 in leap year calculations`() {
        // Start on leap day, end one year later
        val startDate = LocalDate.of(2024, 2, 29)
        val endDate = LocalDate.of(2025, 2, 28)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Should be very close to 1.0 year
        assertThat(result).isCloseTo(
            1.0,
            org.assertj.core.data.Offset
                .offset(0.01)
        )
    }

    @Test
    fun `should demonstrate financial precision for short periods`() {
        // 1 month period for financial accuracy
        val startDate = LocalDate.of(2023, 1, 1)
        val endDate = LocalDate.of(2023, 2, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Expected: 31 days / 365 days = 0.0849...
        assertThat(result).isCloseTo(
            0.0849,
            org.assertj.core.data.Offset
                .offset(0.0001)
        )
    }

    @Test
    fun `should handle very long periods with multiple leap years`() {
        // 10-year period with multiple leap years
        val startDate = LocalDate.of(2020, 1, 1) // Leap year
        val endDate = LocalDate.of(2030, 1, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Should be exactly 10.0 years
        assertThat(result).isCloseTo(
            10.0,
            org.assertj.core.data.Offset
                .offset(0.01)
        )
    }

    @Test
    fun `should provide precise calculation for IRR scenarios`() {
        // Test case that matches the 300% over 10 years scenario mentioned by user
        val startDate = LocalDate.of(2014, 1, 1)
        val endDate = LocalDate.of(2024, 1, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // ACTUAL/ACTUAL convention: 10 years with 2 leap years (2016, 2020)
        // Total days: 3653 (vs 3652.5 for average) so slightly less than 10.0
        assertThat(result).isCloseTo(
            9.999,
            org.assertj.core.data.Offset
                .offset(0.002)
        )
    }

    @Test
    fun `should handle end date before start date gracefully`() {
        val startDate = LocalDate.of(2023, 12, 31)
        val endDate = LocalDate.of(2023, 1, 1)
        val result = dateUtils.calculateYearFraction(startDate, endDate)

        // Should return negative value for reverse period
        assertThat(result).isLessThan(0.0)
        assertThat(result).isCloseTo(
            -1.0,
            org.assertj.core.data.Offset
                .offset(0.01)
        )
    }
}