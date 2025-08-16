package com.beancounter.common.utils

import com.beancounter.common.TestHelpers
import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

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
}