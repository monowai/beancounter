package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.springframework.stereotype.Component
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Date based helper functions.
 *
 * @author mikeh
 * @since 2019-03-12
 */
@Component
class DateUtils {
    //var defaultZone: ZoneId = ZoneId.of("Asia/Singapore")

    private val defaultFormatter = SimpleDateFormat(format)


    fun convert(localDate: LocalDate): LocalDate? {
        val zoned = localDate.atStartOfDay(getZoneId())
        return getDate(zoned.toLocalDate().toString())
    }

    val date: LocalDate?
        get() = getDate(today())

    fun getDate(inDate: String?, zoneId: ZoneId = getZoneId()): LocalDate? {
        return when (inDate) {
            null -> {
                null
            }
            "today" -> {
                return date
            }
            else -> getDate(inDate, "yyyy-MM-dd", zoneId)
        }
    }

    fun getDate(inDate: String?, format: String?, zoneId: ZoneId = getZoneId()): LocalDate? {
        return if (inDate == null) {
            null
        } else getLocalDate(inDate, format)
                ?.atStartOfDay(zoneId)?.toLocalDate()
    }

    fun getLocalDate(inDate: String?, dateFormat: String?): LocalDate? {
        return when (inDate) {
            null -> {
                null
            }
            "today" -> {
                return date
            }
            else -> LocalDate.parse(inDate, DateTimeFormatter.ofPattern(dateFormat))
        }
    }

    fun today(): String {
        return LocalDate.now(getZoneId()).toString()
    }

    fun getOrThrow(inDate: String?): LocalDate {
        try {
            val result = getDate(inDate)
            if (result != null) {
                return result
            }
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        } catch (e: RuntimeException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }
    fun isToday(inDate: String?): Boolean {
        return isToday(inDate, getZoneId())
    }

    fun isToday(inDate: String?, tz: ZoneId): Boolean {
        return if (inDate == null || inDate.isBlank() || "today" == inDate.toLowerCase()) {
            true // Null date is BC is "today"
        } else try {
            val today = defaultFormatter.parse(LocalDate.now(tz).toString())
            val compareWith = defaultFormatter.parse(inDate)
            today.compareTo(compareWith) == 0
        } catch (e: ParseException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        } catch (e: NumberFormatException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }

    fun getDateString(date: LocalDate?): String? {
        return date?.toString()
    }

    companion object {
        @JvmStatic
        fun getZoneId(): ZoneId {
            return ZoneId.of("Asia/Singapore")
        }

        const val format = "yyyy-MM-dd"
        //public var defaultZone: ZoneId = ZoneId.of("Asia/Singapore")
    }
}