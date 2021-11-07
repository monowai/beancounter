package com.beancounter.common

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneOffset.UTC
import java.util.Calendar
import java.util.Date
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
    fun is_TodayAnIso8601String() {
        val calendar = Calendar.Builder()
            .setTimeZone(TimeZone.getTimeZone(UTC))
            .setInstant(Date()).build()
        val now = dateUtils.offsetDateString()
        calendar[Calendar.YEAR]
        assertThat(now)
            .isNotNull
            .startsWith(calendar[Calendar.YEAR].toString())
            .contains("-" + String.format("%02d", calendar[Calendar.MONTH] + 1) + "-")
            .contains("-" + String.format("%02d", calendar[Calendar.DAY_OF_MONTH]))
        assertThat(dateUtils.getDate("2019-11-29")).isNotNull
        dateUtils.getOrThrow("2019-11-29")
    }

    @Test
    fun is_InvalidDateDetected() {
        val invalidDate = "2019-11-07'"
        assertThrows(BusinessException::class.java) { dateUtils.getOrThrow(invalidDate) }
    }

    @Test
    fun is_LocalDateEqualToToday() {
        val today = dateUtils.offsetDateString()
        val nowInTz = LocalDate.now(UTC)
        assertThat(nowInTz.toString()).isEqualTo(today)
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
