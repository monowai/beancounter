package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TimeZone

/**
 * Helper service for BC Date functions.
 *
 * @author mikeh
 * @since 2019-03-12
 */

@Service
class DateUtils(
    @Value("\${beancounter.zone:#{null}}")
    val defaultZone: String = TimeZone.getDefault().id,
) {

    private val defaultFormatter = SimpleDateFormat(format)

    fun convert(localDate: LocalDate): LocalDate? {
        val zoned = localDate.atStartOfDay(getZoneId())
        return getDate(zoned.toLocalDate().toString())
    }

    fun offset(date: String = today()): OffsetDateTime {
        return OffsetDateTime.ofInstant(
            LocalDateTime.of(getDate(date), LocalTime.now()).toInstant(UTC),
            getZoneId()
        )
    }

    fun offsetDateString(date: String = today()): String {
        return getDateString(offset(date).toLocalDate())
    }

    val date: LocalDate
        get() = getDate(today())

    fun today() = LocalDate.now(UTC).toString()

    fun getDateString(date: LocalDate) = date.toString()

    fun getZoneId(): ZoneId = ZoneId.of(defaultZone)

    fun getDate(inDate: String?, zoneId: ZoneId = getZoneId()): LocalDate {
        return when (inDate) {
            null -> {
                date
            }
            today -> {
                return date
            }
            else -> getDate(inDate, "yyyy-MM-dd", zoneId)
        }
    }

    fun getDate(inDate: String?, dateFormat: String, zoneId: ZoneId = getZoneId()): LocalDate {
        return if (inDate == null) {
            date
        } else getLocalDate(inDate, dateFormat)
            .atStartOfDay(zoneId).toLocalDate()
    }

    fun getLocalDate(inDate: String?, dateFormat: String = format): LocalDate {
        return if (inDate == null || inDate.lowercase(Locale.getDefault()) == today) {
            date
        } else {
            LocalDate.parse(inDate, DateTimeFormatter.ofPattern(dateFormat))
        }
    }

    fun getOrThrow(inDate: String?): LocalDate {
        try {
            return getDate(inDate)
        } catch (e: DateTimeParseException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }

    fun isToday(inDate: String?): Boolean {
        return if (inDate == null || inDate.isBlank() || today == inDate.lowercase(Locale.getDefault())) {
            true // Null date is BC is "today"
        } else try {
            val today = defaultFormatter.parse(offset().toLocalDate().toString())
            val compareWith = defaultFormatter.parse(inDate)
            today.compareTo(compareWith) == 0
        } catch (e: ParseException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }

    companion object {
        const val format = "yyyy-MM-dd"
        const val today = "today"
    }
}
