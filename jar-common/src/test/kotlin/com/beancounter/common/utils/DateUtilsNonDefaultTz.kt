package com.beancounter.common.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Tests for the DateUtils class focusing on handling different time zones.
 * This test class verifies the functionality of DateUtils, particularly ensuring that
 * time zones are correctly handled and that date calculations correctly reflect the time zone differences.
 *
 * <p>Each test method assesses a specific aspect of the DateUtils class:</p>
 * <ul>
 *     <li>{@code zoneId should match the expected default and specific time zones} - Validates that the default
 *         and specified time zone settings are correctly applied and retrieved.</li>
 *     <li>{@code dates across different time zones are handled correctly} - Ensures that dates are accurately processed
 *         and formatted when working across different time zones, particularly verifying that the transformation
 *         from a given date string to an offset date string and back retains consistency.</li>
 *     <li>{@code dates should correctly handle different time zones} - Further tests the handling of time zone differences
 *         by checking consistency between formatted dates and their corresponding offset dates within a given non-default
 *         time zone setting.</li>
 *     <li>{@code zoneId is correct for external time zones} - Checks that the DateUtils class can correctly initialize
 *         with external, non-default time zones and retrieves the expected time zone ID.</li>
 * </ul>
 *
 * <p>This class uses a non-default time zone setting to ensure that the DateUtils class is robust and functions
 * correctly across different geographical settings.</p>
 */
class DateUtilsNonDefaultTz {
    private lateinit var nzDateUtils: DateUtils
    private lateinit var dateUtils: DateUtils
    private val requestDate = "2020-11-11"

    @BeforeEach
    fun setupDateUtils() {
        nzDateUtils = DateUtils(TimeZone.getTimeZone("NZ").id)
        dateUtils = DateUtils()
    }

    @Test
    fun `zoneId should match the expected default and specific time zones`() {
        assertThat(dateUtils.zoneId.id).isEqualTo(TimeZone.getDefault().id)
        assertThat(nzDateUtils.zoneId.id).isEqualTo("NZ")
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
    fun `zoneId is correct for external time zones`() {
        val laDates = DateUtils("America/Los_Angeles")
        assertThat(laDates.zoneId.id).isEqualTo("America/Los_Angeles")
    }
}