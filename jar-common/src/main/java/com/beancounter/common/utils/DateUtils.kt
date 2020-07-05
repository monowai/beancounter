package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import org.springframework.stereotype.Component
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

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
    fun getLastMarketDate(seedDate: LocalDate, targetZone: ZoneId?): LocalDate {
        var days = 1
        if (!isToday(getDateString(seedDate))) {
            days = 0
        }
        return getLastMarketDate(seedDate, targetZone, days)
    }

    /**
     * Identify a date to query a market on taking into account timezones and working days.
     * Always takes One from seedDate. Then subtracts a day until it finds a working one.
     * For instance - Sunday 7th in Singapore will result to Friday 5th in New York
     *
     * @param seedDate   usually Today requesting in callers timezone
     * @param targetZone market to locate requestedDate on
     * @return resolved Date
     */
    fun getLastMarketDate(seedDate: LocalDate, targetZone: ZoneId?, daysToSubtract: Int): LocalDate {
        Objects.requireNonNull(seedDate)
        Objects.requireNonNull(targetZone)
        var result = seedDate.minusDays(daysToSubtract.toLong())
        while (!isWorkDay(result)) {
            result = result.minusDays(1)
        }
        return result
    }

    fun convert(localDate: LocalDate): LocalDate? {
        val zoned = localDate.atStartOfDay(getZoneId())
        return getDate(zoned.toLocalDate().toString())
    }

    fun isWorkDay(evaluate: LocalDate): Boolean {
        // Naive implementation that is only aware of Western markets
        return if (evaluate.dayOfWeek == DayOfWeek.SUNDAY) {
            false
        } else {
            evaluate.dayOfWeek != DayOfWeek.SATURDAY
        }

        // ToDo: market holidays...
    }

    val date: LocalDate?
        get() = getDate(today())

    fun getDate(inDate: String?): LocalDate? {
        return when (inDate) {
            null -> {
                null
            }
            "today" -> {
                return date
            }
            else -> getDate(inDate, "yyyy-MM-dd")
        }
    }

    fun getDate(inDate: String?, format: String?): LocalDate? {
        return if (inDate == null) {
            null
        } else getLocalDate(inDate, format)
                ?.atStartOfDay(getZoneId())?.toLocalDate()
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
        return if (inDate == null || inDate.isBlank() || "today" == inDate.toLowerCase()) {
            true // Null date is BC is "today"
        } else try {
            val today = defaultFormatter.parse(today())
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