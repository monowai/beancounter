package com.beancounter.common.utils

import com.beancounter.common.model.Market
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZonedDateTime

/**
 * Market TZ utilities to calculate close dates
 */
@Service
class PreviousClosePriceDate(
    private val dateUtils: DateUtils
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun getPriceDate(
        utcRequestDateTime: ZonedDateTime,
        market: Market,
        isCurrent: Boolean = dateUtils.isToday(utcRequestDateTime.toLocalDate().toString())
    ): LocalDate =
        if (isCurrent) {
            val pricesAvailable =
                getPricesAvailable(
                    utcRequestDateTime,
                    market
                )
            val daysToSubtract =
                getDaysToSubtract(
                    market,
                    utcRequestDateTime.toLocalDateTime(),
                    pricesAvailable.toLocalDateTime()
                )

            log.trace(
                "utc: $utcRequestDateTime, subtract: $daysToSubtract, marketLocal: $utcRequestDateTime, " +
                    "marketCloses: $pricesAvailable, timezone: ${dateUtils.zoneId}"
            )
            getPriceDate(
                utcRequestDateTime,
                daysToSubtract
            )
        } else {
            // Just account for work days
            log.debug("Returning last working day relative to requested date")
            getPriceDate(
                utcRequestDateTime,
                0
            )
        }

    fun getPricesAvailable(
        marketLocal: ZonedDateTime,
        market: Market
    ): OffsetDateTime =
        OffsetDateTime.of(
            marketLocal.toLocalDate(),
            market.priceTime,
            marketLocal.offset
        )

    private fun getDaysToSubtract(
        market: Market,
        requestedDate: LocalDateTime,
        pricesAvailable: LocalDateTime
    ): Int {
        // Is requested datetime on or after when prices are available?
        if (requestedDate >= pricesAvailable) {
            return 0 // at this point prices are updated
        }
        return market.daysToSubtract
    }

    /**
     * Finds the closest market price date relative to the requested date
     *
     * @param seedDate   usually Today
     * @return resolved Date
     */
    fun getPriceDate(
        seedDate: ZonedDateTime,
        daysToSubtract: Int
    ): LocalDate {
        var notionalDateTime = seedDate.minusDays(daysToSubtract.toLong())
        while (!isTradingDay(notionalDateTime)) {
            notionalDateTime = notionalDateTime.minusDays(1)
        }
        return notionalDateTime.toLocalDate()
    }

    fun isTradingDay(dateTime: ZonedDateTime): Boolean {
        // Naive implementation that is only aware of Western markets
        // ToDo: market holidays...
        val weekend =
            if (dateTime.dayOfWeek == DayOfWeek.SUNDAY) {
                false
            } else {
                dateTime.dayOfWeek != DayOfWeek.SATURDAY
            }
        return weekend
    }
}