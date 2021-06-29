package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TimeZone

/**
 * Date based helper functions.
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

    val date: LocalDate
        get() = getDate(today())

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

    fun today(): String {
        return LocalDate.now(getZoneId()).toString()
    }

    fun getOrThrow(inDate: String?): LocalDate {
        try {
            return getDate(inDate)
        } catch (e: DateTimeParseException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }

    fun isToday(inDate: String?): Boolean {
        return isToday(inDate, getZoneId())
    }

    fun isToday(inDate: String?, tz: ZoneId): Boolean {
        return if (inDate == null || inDate.isBlank() || today == inDate.lowercase(Locale.getDefault())) {
            true // Null date is BC is "today"
        } else try {
            val today = defaultFormatter.parse(LocalDate.now(tz).toString())
            val compareWith = defaultFormatter.parse(inDate)
            today.compareTo(compareWith) == 0
        } catch (e: ParseException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }

    fun getDateString(date: LocalDate): String {
        return date.toString()
    }

    fun getZoneId(): ZoneId {
        return ZoneId.of(defaultZone)
    }

    companion object {
        const val format = "yyyy-MM-dd"
        const val today = "today"
    }
}
