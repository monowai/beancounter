package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.util.TimeZone

internal class DateUtilsTest {
    private val dateUtils = DateUtils()

    @Test
    fun is_Today() {
        assertThat(dateUtils.isToday(dateUtils.today())).isTrue
        assertThat(dateUtils.isToday()).isTrue
        assertThat(dateUtils.isToday("")).isTrue
        assertThat(dateUtils.isToday(" ")).isTrue
        assertThat(dateUtils.isToday("ToDay")).isTrue
    }

    @Test
    fun is_ZonedDateCorrect() {
        assertThat(dateUtils.getZoneId().id).isEqualTo(TimeZone.getDefault().id)
        val pst = "America/Los_Angeles"
        val laTz = TimeZone.getTimeZone(pst)
        val laDates = DateUtils(pst)
        assertThat(laDates.defaultZone).isEqualTo(laTz.id)
        assertThat(laDates.getZoneId().id).isEqualTo(TimeZone.getTimeZone(pst).id)
    }

    @Test
    fun is_EarlierTimezone() {
        val requestDate = dateUtils.today()
        val auDateUtils = DateUtils(TimeZone.getTimeZone("NZ").id)
        val offsetDate = auDateUtils.offsetDateString(requestDate)
        val testDate = auDateUtils.getDate(offsetDate)
        assertThat(testDate)
            .isEqualTo(auDateUtils.offsetNow(requestDate).toLocalDate())
    }

    @Test
    fun is_AuFixedDate() {
        val requestDate = "2020-11-11"
        val auDateUtils = DateUtils(TimeZone.getTimeZone("NZ").id)
        val offsetDate = auDateUtils.offsetDateString(requestDate)
        val testDate = auDateUtils.getDate(offsetDate)
        assertThat(testDate)
            .isEqualTo(auDateUtils.offsetNow(requestDate).toLocalDate())
            .isEqualTo(requestDate)
    }

    @Test
    fun is_InvalidDateDetected() {
        val invalidDate = "2019-11-07'"
        assertThrows(BusinessException::class.java) { dateUtils.getOrThrow(invalidDate) }
    }

    @Test
    fun is_ValidDateNotThrown() {
        assertThat(dateUtils.getOrThrow()).isNotNull()
    }

    @Test
    fun is_DateString() {
        val default = dateUtils.offsetDateString()
        assertThat(dateUtils.getDate()).isEqualTo(default)
        assertThat(dateUtils.getDate("today")).isEqualTo(default)
    }

    @Test
    fun is_LocalDateString() {
        val default = dateUtils.date
        assertThat(dateUtils.getLocalDate()).isEqualTo(default)
        assertThat(dateUtils.getLocalDate("today")).isEqualTo(default)
        assertThat(dateUtils.getLocalDate("ToDay")).isEqualTo(default)
    }

    @Test
    fun is_ParseException() {
        assertThrows(BusinessException::class.java) { dateUtils.isToday("ABC-MM-11") }
    }
}
