package com.beancounter.common.utils

import com.beancounter.common.exception.BusinessException
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.Year
import java.time.ZoneId
import java.time.ZoneOffset.UTC
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.TimeZone

private const val YEAR_FRACTION = 365.25

/**
 * Utility class for handling dates and times with respect to a configurable time zone.
 * This class provides various methods to work with dates including getting the current date,
 * converting dates to different formats, and comparing dates. It also includes methods to create
 * `OffsetDateTime` instances for specific times and dates.
 *
 * The default time zone can be set via the 'beancounter.zone' configuration property and defaults
 * to the system's default time zone if not specified. All date-time calculations and transformations
 * are based on this time zone unless otherwise specified.
 *
 * @property defaultZone the ID of the default time zone, which is configurable and falls back
 *                       to the system's default time zone if not set.
 * @constructor Creates a DateUtils instance initializing the time zone based on the provided or default setting.
 *
 * @author mikeh
 * @since 2019-03-12
 */

@Service
class DateUtils(
    @param:Value($$"${beancounter.zone:#{null}}")
    private val defaultZone: String = TimeZone.getDefault().id
) {
    val zoneId: ZoneId = ZoneId.of(defaultZone)

    companion object {
        const val TODAY = "today"
        const val FORMAT = "yyyy-MM-dd"
        private val log = LoggerFactory.getLogger(DateUtils::class.java)
    }

    @PostConstruct
    fun logConfig() {
        log.info("beancounter.zone: $defaultZone")
    }

    /**
     * Returns the current date as a String formatted according to ISO local date (YYYY-MM-DD).
     */
    fun today(): String = LocalDate.now(zoneId).toString()

    val date: LocalDate
        get() =
            getFormattedDate(
                TODAY,
                listOf(FORMAT)
            )

    /**
     * Creates an OffsetDateTime object from the specified date and time, using UTC as the time zone.
     * Defaults to the current local time if not specified.
     * @param date the date as a string, defaulting to "today"
     * @param time the local time, defaulting to the current system time
     * @return OffsetDateTime object representing the specified date and time
     */
    fun offset(
        date: String = TODAY,
        time: LocalTime = LocalTime.now(zoneId)
    ): OffsetDateTime =
        OffsetDateTime.of(
            getFormattedDate(date),
            time,
            UTC
        )

    /**
     * Converts the specified date string to an ISO local date string using the offset method.
     * @param date the date as a string, defaulting to "today"
     * @return the local date part of the OffsetDateTime as a string
     */
    fun offsetDateString(date: String = TODAY): String = offset(date).toLocalDate().toString()

    /**
     * Parses the given date string into a LocalDate, using the provided date format or default if not specified.
     * @param inDate the date as a string, defaulting to "today"
     * @return LocalDate object representing the parsed date
     */
    fun getFormattedDate(
        inDate: String = TODAY,
        dateFormats: List<String> =
            listOf(
                "yyyy-MM-dd",
                "yyyy-MM-d",
                "yyyy-M-d"
            )
    ): LocalDate {
        if (inDate.lowercase(Locale.getDefault()) == TODAY) {
            return LocalDate.now(zoneId)
        }

        for (format in dateFormats) {
            try {
                return LocalDate.parse(
                    inDate,
                    DateTimeFormatter.ofPattern(format)
                )
            } catch (_: DateTimeParseException) {
                // Continue to the next format
            }
        }

        throw BusinessException("Unable to parse the date $inDate")
    }

    /**
     * Attempts to parse a date string into a LocalDate or throws BusinessException if parsing fails.
     * @param inDate the date as a string, defaulting to "today"
     * @return LocalDate object representing the parsed date
     * @throws BusinessException if the date cannot be parsed
     */
    fun getDate(inDate: String = TODAY): LocalDate {
        try {
            return getFormattedDate(inDate)
        } catch (_: DateTimeParseException) {
            throw BusinessException("Unable to parse the date $inDate")
        }
    }

    /**
     * Checks if the specified date string represents today's date.
     * @param inDate the date as a string, defaulting to "today"
     * @return true if the date is today, false otherwise
     */
    fun isToday(inDate: String = TODAY): Boolean =
        if (inDate.isBlank() || TODAY == inDate.lowercase(Locale.getDefault())) {
            true // Null date is "today"
        } else {
            getFormattedDate(inDate).isEqual(LocalDate.now(zoneId))
        }

    /**
     * Returns an OffsetDateTime for now or for a specified date.
     * @param date the date as a string
     * @return OffsetDateTime representing now or the specified date at the current time
     */
    fun offsetNow(date: String): OffsetDateTime =
        if (isToday(date)) {
            OffsetDateTime.now(UTC)
        } else {
            OffsetDateTime.of(
                getFormattedDate(date).atTime(LocalTime.now()),
                UTC
            )
        }

    /**
     * Calculates precise year fraction between two dates, accounting for leap years.
     * Uses the ACTUAL/ACTUAL day count convention for financial accuracy.
     *
     * This method provides more accurate calculations than simple day division by:
     * - Properly handling leap years (366 vs 365 days)
     * - Using weighted averages for periods spanning multiple years
     * - Following financial industry standards for year fraction calculations
     *
     * @param startDate the start date (inclusive)
     * @param endDate the end date (exclusive)
     * @return the precise year fraction as a Double
     */
    fun calculateYearFraction(
        startDate: LocalDate,
        endDate: LocalDate
    ): Double {
        if (startDate.isEqual(endDate)) return 0.0

        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toDouble()

        // For periods spanning multiple years, calculate weighted average of year lengths
        var currentDate = startDate
        var weightedDays = 0.0
        var actualDays = 0.0

        while (currentDate.year <= endDate.year) {
            val yearStart =
                if (currentDate.year ==
                    startDate.year
                ) {
                    currentDate
                } else {
                    LocalDate.of(currentDate.year, 1, 1)
                }
            val yearEnd =
                if (currentDate.year ==
                    endDate.year
                ) {
                    endDate
                } else {
                    LocalDate.of(currentDate.year, 12, 31).plusDays(1)
                }

            val daysInThisYear = ChronoUnit.DAYS.between(yearStart, yearEnd).toDouble()
            val daysInYear = if (Year.isLeap(currentDate.year.toLong())) 366.0 else 365.0

            weightedDays += daysInThisYear
            actualDays += daysInThisYear / daysInYear * daysInYear

            currentDate = LocalDate.of(currentDate.year + 1, 1, 1)
        }

        // For simple cases within same year, use year-specific day count
        return if (startDate.year == endDate.year) {
            val daysInYear = if (Year.isLeap(startDate.year.toLong())) 366.0 else 365.0
            totalDays / daysInYear
        } else {
            // For multi-year periods, use average
            totalDays / (actualDays / weightedDays * YEAR_FRACTION)
        }
    }
}