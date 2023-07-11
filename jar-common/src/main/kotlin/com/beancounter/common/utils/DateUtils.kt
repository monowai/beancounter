package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.OffsetTime.now
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TimeZone

/**
 * Helper service for BC Date functions. Assume dates to be in UTC.
 *
 * @author mikeh
 * @since 2019-03-12
 */

@Service
class DateUtils(
    @Value("\${beancounter.zone:#{null}}")
    val defaultZone: String = TimeZone.getDefault().id,
) {

    fun today() = LocalDate.now(UTC).toString()

    fun getZoneId(): ZoneId = ZoneId.of(defaultZone)

    fun offset(date: String = today): OffsetDateTime {
        return OffsetDateTime.of(getDate(date), now().toLocalTime(), UTC)
    }

    fun offsetDateString(date: String = today): String {
        return offset(date).toLocalDate().toString()
    }

    val date: LocalDate
        get() = getDate(today, format, ZoneId.of(defaultZone))

    fun getDate(inDate: String = today, zoneId: ZoneId = getZoneId()): LocalDate {
        return when (inDate) {
            today -> {
                return LocalDate.now(zoneId)
            }

            else -> getDate(inDate, format, zoneId)
        }
    }

    fun getDate(inDate: String = today, dateFormat: String = format, zoneId: ZoneId = getZoneId()): LocalDate {
        return getLocalDate(inDate, dateFormat, zoneId)
            .atStartOfDay(zoneId).toLocalDate()
    }

    fun getLocalDate(inDate: String = today, dateFormat: String = format, zoneId: ZoneId = getZoneId()): LocalDate {
        return if (inDate.lowercase(Locale.getDefault()) == today) {
            LocalDate.now(zoneId)
        } else {
            LocalDate.parse(inDate, DateTimeFormatter.ofPattern(dateFormat))
        }
    }

    fun getOrThrow(inDate: String = today): LocalDate {
        try {
            return getDate(inDate)
        } catch (e: DateTimeParseException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }

    fun isToday(inDate: String = today): Boolean {
        return if (inDate.isBlank() || today == inDate.lowercase(Locale.getDefault())) {
            true // Null date is BC is "today"
        } else {
            try {
                val today = defaultFormatter.parse(today())
                val compareWith = defaultFormatter.parse(inDate)
                compareWith >= today
            } catch (e: ParseException) {
                throw BusinessException(String.format("Unable to parse the date %s", inDate))
            }
        }
    }

    fun offsetNow(date: String): OffsetDateTime {
        if (isToday(date)) {
            return OffsetDateTime.now(UTC)
        }
        return OffsetDateTime.ofInstant(
            getDate(date).atTime(OffsetDateTime.now().toLocalTime()).toInstant(UTC),
            ZoneId.of(UTC.id),
        )
    }

    companion object {
        const val format = "yyyy-MM-dd"
        const val today = "today"
        val defaultFormatter = SimpleDateFormat(format)
    }
}
