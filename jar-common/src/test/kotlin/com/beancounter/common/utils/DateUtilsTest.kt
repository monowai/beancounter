package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Test class for DateUtils. This class comprehensively tests the functionality of the DateUtils class,
 * which includes validation of date manipulation and formatting operations across different time zones.
 * It ensures that the methods behave as expected under various conditions, including handling valid and
 * invalid date inputs, recognizing 'today' correctly, and matching time zones and formatted outputs.
 *
 * The class tests several key functionalities:
 * - The ability of the isToday method to correctly determine if a given date string represents the current date.
 * - The correctness of time zone handling, ensuring that the configured time zone is respected in all date-time calculations.
 * - The functionality of handling dates across different time zones, specifically testing if the dates are processed
 *   correctly when using time zones like NZ (New Zealand) and comparing it against system defaults and other specified zones.
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
    private lateinit var nzDateUtils: DateUtils

    @BeforeEach
    fun setUp() {
        dateUtils = DateUtils()
        nzDateUtils = DateUtils(TimeZone.getTimeZone("NZ").id)
    }

    @Test
    fun `isToday should recognize today correctly`() {
        with(dateUtils) {
            assertThat(isToday(dateUtils.today())).isTrue()
            assertThat(isToday()).isTrue()
            assertThat(isToday("")).isTrue()
            assertThat(isToday(" ")).isTrue()
            assertThat(isToday("ToDay")).isTrue()
        }
    }

    @Test
    fun `zoneId should match the expected default and specific time zones`() {
        assertThat(dateUtils.zoneId.id).isEqualTo(TimeZone.getDefault().id)
        assertThat(nzDateUtils.zoneId.id).isEqualTo("NZ")
    }

    @Test
    fun `zoneId is correct for external time zones`() {
        val laDates = DateUtils("America/Los_Angeles")
        assertThat(laDates.zoneId.id).isEqualTo("America/Los_Angeles")
    }

    private val requestDate = "2020-11-11"

    @Test
    fun `valid date processing should not throw exceptions`() {
        assertThat(dateUtils.getDate()).isNotNull()
        assertThat(dateUtils.getDate(requestDate)).isNotNull()
    }

    @Test
    fun `dates across different time zones are handled correctly`() {
        val offsetDate = nzDateUtils.offsetDateString(requestDate)
        assertThat(nzDateUtils.getFormattedDate(offsetDate)).isEqualTo(requestDate)
    }

    @Test
    fun `dates should correctly handle different time zones`() {
        val offsetDate = nzDateUtils.offsetDateString(requestDate)
        val testDate = nzDateUtils.getFormattedDate(offsetDate)

        assertThat(testDate).isEqualTo(requestDate)
        assertThat(nzDateUtils.offsetNow(requestDate).toLocalDate()).isEqualTo(requestDate)
    }

    @Test
    fun `invalid date string should trigger BusinessException`() {
        assertThrows(BusinessException::class.java) {
            dateUtils.getDate("2019-11-07'")
        }
    }

    @Test
    fun `offsetDateString should match formatted date for today`() {
        val default = dateUtils.offsetDateString()
        assertThat(dateUtils.getFormattedDate()).isEqualTo(default)
        assertThat(dateUtils.getFormattedDate("today")).isEqualTo(default)
    }

    @Test
    fun `formatted date should match the local date for specific and today inputs`() {
        val default = dateUtils.date
        with(dateUtils) {
            assertThat(getFormattedDate()).isEqualTo(default)
            assertThat(getFormattedDate("today")).isEqualTo(default)
            assertThat(getFormattedDate("ToDay")).isEqualTo(default)
        }
    }

    @Test
    fun `getDate should handle non-date string input with BusinessException`() {
        assertThrows(BusinessException::class.java) {
            dateUtils.getDate("ABC-MM-11")
        }
    }
}
