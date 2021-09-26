package com.beancounter.common.utils

import com.beancounter.common.model.Market
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZonedDateTime

/**
 * Market TZ utilities to calculate close dates
 */
@Component
class PreviousClosePriceDate(private val dateUtils: DateUtils) {

    fun getPriceDate(
        localDateTime: LocalDateTime, // Callers date/time
        market: Market,
        isCurrent: Boolean = dateUtils.isToday(dateUtils.getDateString(localDateTime.toLocalDate()))
    ): LocalDate {
        return if (isCurrent) {
            // Bug here - localDateTime should be a zonedDateTime as we can't assume all clients are in the same TZ
            val zonedLocal = ZonedDateTime.ofLocal(localDateTime, dateUtils.getZoneId(), null)
            val marketLocal = zonedLocal.withZoneSameInstant(market.timezone.toZoneId())
            val zonedRemote = ZonedDateTime.ofLocal(
                LocalDateTime.of(marketLocal.toLocalDate(), market.priceTime),
                market.timezone.toZoneId(),
                null
            )

            getPriceDate(marketLocal.toLocalDateTime(), getDaysToSubtract(market, zonedLocal, zonedRemote))
        } else {
            // Just account for market open
            getPriceDate(localDateTime, 0)
        }
    }

    private fun getDaysToSubtract(
        market: Market,
        zonedLocal: ZonedDateTime,
        zonedRemote: ZonedDateTime
    ): Int {
        var daysToSubtract = market.daysToSubtract
        // Is requested date on or after when prices are available?
        if (zonedLocal.toLocalDate() >= zonedRemote.withZoneSameInstant(zonedLocal.zone).toLocalDate()) {
            daysToSubtract = 0
        }
        return daysToSubtract
    }

    fun getPriceDate(date: LocalDate, daysToSubtract: Int): Any {
        return getPriceDate(date.atStartOfDay(), daysToSubtract)
    }

    /**
     * Finds the closest market price date relative to the requested date
     *
     * @param seedDate   usually Today
     * @return resolved Date
     */
    fun getPriceDate(seedDate: LocalDateTime, daysToSubtract: Int): LocalDate {
        var notionalDateTime = seedDate.minusDays(daysToSubtract.toLong())
        while (!isMarketOpen(notionalDateTime)) {
            notionalDateTime = notionalDateTime.minusDays(1)
        }
        return notionalDateTime.toLocalDate()
    }

    fun isMarketOpen(dateTime: LocalDateTime): Boolean {
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
