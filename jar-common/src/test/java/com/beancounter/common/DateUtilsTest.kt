package com.beancounter.common

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

internal class DateUtilsTest {
    private val dateUtils = DateUtils()

    @Test
    fun is_Today() {
        assertThat(dateUtils.isToday(dateUtils.today())).isTrue
        assertThat(dateUtils.isToday(null)).isTrue
        assertThat(dateUtils.isToday("")).isTrue
        assertThat(dateUtils.isToday(" ")).isTrue
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
            .setTimeZone(TimeZone.getTimeZone(dateUtils.getZoneId()))
            .setInstant(Date()).build()
        val now = dateUtils.today()
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
    fun is_NullIsoDate() {
        assertThat(dateUtils.getDate(null)).isNull()
        assertThat(dateUtils.getDate(null, "yyyy-MM-dd")).isNull()
        assertThat(dateUtils.getLocalDate(null, "yyyy-MM-dd")).isNull()
    }

    @Test
    fun is_LocalDateEqualToToday() {
        val today = dateUtils.today()
        val nowInTz = LocalDate.now(dateUtils.getZoneId())
        assertThat(nowInTz.toString()).isEqualTo(today)
    }

    @Test
    fun is_DateString() {
        assertThat(dateUtils.getDateString(null)).isNull()
        assertThat(dateUtils.getDateString(monday)).isEqualTo("2019-10-21")
    }

    @Test
    fun is_ParseException() {
        assertThrows(BusinessException::class.java) { dateUtils.isToday("ABC-MM-11") }
    }

    private val monday: LocalDate
        get() = dateUtils.getDate("2019-10-21")!!
}
