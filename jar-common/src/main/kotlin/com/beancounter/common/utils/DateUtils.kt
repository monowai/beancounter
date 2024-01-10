package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.OffsetTime.now
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import java.util.TimeZone

/**
 * Helper service for BC Date functions. Assumes dates to be in UTC.
 *
 * @author mikeh
 * @since 2019-03-12
 */

@Service
class DateUtils(
    @Value("\${beancounter.zone:#{null}}")
    val defaultZone: String = TimeZone.getDefault().id,
) {
    @PostConstruct
    fun logConfig() {
        log.info("beancounter.zone: ${getZoneId().id}")
    }

    fun today() = LocalDate.now(UTC).toString()

    fun getZoneId(): ZoneId = ZoneId.of(defaultZone)

    fun offset(date: String = TODAY): OffsetDateTime {
        return offset(date, now().toLocalTime())
    }

    fun offset(
        date: String = TODAY,
        localTime: LocalTime = now().toLocalTime(),
    ): OffsetDateTime {
        return OffsetDateTime.of(getDate(date), localTime, UTC)
    }

    fun offsetDateString(date: String = TODAY): String {
        return offset(date).toLocalDate().toString()
    }

    val date: LocalDate
        get() = getDate(TODAY, FORMAT, ZoneId.of(defaultZone))

    fun getDate(
        inDate: String = TODAY,
        zoneId: ZoneId = getZoneId(),
    ): LocalDate {
        return when (inDate) {
            TODAY -> {
                return LocalDate.now(zoneId)
            }

            else -> getDate(inDate, FORMAT, zoneId)
        }
    }

    fun getDate(
        inDate: String = TODAY,
        dateFormat: String = FORMAT,
        zoneId: ZoneId = getZoneId(),
    ): LocalDate {
        return getLocalDate(inDate, dateFormat, zoneId)
            .atStartOfDay(zoneId).toLocalDate()
    }

    fun getLocalDate(
        inDate: String = TODAY,
        dateFormat: String = FORMAT,
        zoneId: ZoneId = getZoneId(),
    ): LocalDate {
        return if (inDate.lowercase(Locale.getDefault()) == TODAY) {
            LocalDate.now(zoneId)
        } else {
            LocalDate.parse(inDate, DateTimeFormatter.ofPattern(dateFormat))
        }
    }

    fun getOrThrow(inDate: String = TODAY): LocalDate {
        try {
            return getDate(inDate)
        } catch (e: DateTimeParseException) {
            throw BusinessException(String.format("Unable to parse the date %s", inDate))
        }
    }

    fun isToday(inDate: String = TODAY): Boolean {
        return if (inDate.isBlank() || TODAY == inDate.lowercase(Locale.getDefault())) {
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
        const val FORMAT = "yyyy-MM-dd"
        const val TODAY = "today"
        val defaultFormatter = SimpleDateFormat(FORMAT)
        val log = LoggerFactory.getLogger(this::class.java)
    }
}
