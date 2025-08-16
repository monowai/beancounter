package com.beancounter.common.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.TimeZone

/**
 * Test suite for DateUtils focusing on time zone handling and non-default time zone scenarios.
 *
 * This class tests:
 * - Time zone ID validation for default and specific time zones
 * - Date processing across different time zones
 * - Consistency between formatted dates and offset dates
 * - External time zone initialization and validation
 *
 * This class uses non-default time zone settings to ensure DateUtils functions correctly
 * across different geographical settings and time zone configurations.
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
    fun `should match expected default and specific time zones`() {
        assertThat(dateUtils.zoneId.id).isEqualTo(TimeZone.getDefault().id)
        assertThat(nzDateUtils.zoneId.id).isEqualTo("NZ")
    }

    @Test
    fun `should handle dates across different time zones correctly`() {
        val offsetDate = nzDateUtils.offsetDateString(requestDate)
        assertThat(nzDateUtils.getFormattedDate(offsetDate)).isEqualTo(requestDate)
    }

    @Test
    fun `should correctly handle different time zones`() {
        val offsetDate = nzDateUtils.offsetDateString(requestDate)
        val testDate = nzDateUtils.getFormattedDate(offsetDate)

        assertThat(testDate).isEqualTo(requestDate)
        assertThat(nzDateUtils.offsetNow(requestDate).toLocalDate()).isEqualTo(requestDate)
    }

    @Test
    fun `should have correct zoneId for external time zones`() {
        val laDates = DateUtils("America/Los_Angeles")
        assertThat(laDates.zoneId.id).isEqualTo("America/Los_Angeles")
    }
}