package com.beancounter.common.utils

import com.beancounter.common.model.Market
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Market TZ utilities to calculate close dates
 */
@Component
class PreviousClosePriceDate(private val dateUtils: DateUtils) {

    fun getPriceDate(
        localOffsetDateTime: OffsetDateTime, // Callers requested date in UTC
        market: Market,
        isCurrent: Boolean = dateUtils.isToday(localOffsetDateTime.toLocalDate().toString()),
    ): LocalDate {
        return if (isCurrent) {
            val zonedLocal = ZonedDateTime.ofLocal(
                localOffsetDateTime.toLocalDateTime(),
                ZoneOffset.UTC,
                null,
            )

            val marketLocal = zonedLocal.withZoneSameInstant(market.timezone.toZoneId())
            val marketOffset = OffsetDateTime.of(
                marketLocal.toLocalDate(),
                market.priceTime,
                marketLocal.offset,
            )

            getPriceDate(
                marketLocal.toLocalDateTime(),
                getDaysToSubtract(market, zonedLocal, marketOffset),
            )
        } else {
            // Just account for work days
            getPriceDate(localOffsetDateTime.toLocalDateTime(), 0)
        }
    }

    private fun getDaysToSubtract(
        market: Market,
        requestedDate: ZonedDateTime,
        pricesAvailable: OffsetDateTime,
    ): Int {
        // Is requested datetime on or after when prices are available?
        if (requestedDate.toLocalDateTime() >= pricesAvailable.toLocalDateTime()) {
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
    fun getPriceDate(seedDate: LocalDateTime, daysToSubtract: Int): LocalDate {
        var notionalDateTime = seedDate.minusDays(daysToSubtract.toLong())
        while (!isTradingDay(notionalDateTime)) {
            notionalDateTime = notionalDateTime.minusDays(1)
        }
        return notionalDateTime.toLocalDate()
    }

    fun isTradingDay(dateTime: LocalDateTime): Boolean {
        // Naive implementation that is only aware of Western markets
        // ToDo: market holidays...
        val weekend = if (dateTime.dayOfWeek == DayOfWeek.SUNDAY) {
            false
        } else {
            dateTime.dayOfWeek != DayOfWeek.SATURDAY
        }
        return weekend
    }
}
