package com.beancounter.common.utils

import com.beancounter.common.model.Market
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime

@Component
/**
 * Market TZ utilities to calculate close dates
 */
class PreviousClosePriceDate(private val dateUtils: DateUtils) {

    fun getPriceDate(date: LocalDate, market: Market): LocalDate {
        return getPriceDate(date.atTime(LocalTime.now(dateUtils.getZoneId())), market)
    }

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

            var daysToSubtract = market.daysToSubtract
            // Is requested date on or after when prices are available?
            if (zonedLocal.toLocalDate() >= zonedRemote.withZoneSameInstant(zonedLocal.zone).toLocalDate()) {
                daysToSubtract = 0
            }
            getPriceDate(marketLocal.toLocalDateTime(), daysToSubtract)
        } else {
            // Just account for market open
            getPriceDate(localDateTime, 0)
        }
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
        var result = seedDate.minusDays(daysToSubtract.toLong())
        while (!isMarketOpen(result.toLocalDate())) {
            result = result.minusDays(1)
        }
        return result.toLocalDate()
    }

    fun isMarketOpen(evaluate: LocalDate): Boolean {
        // Naive implementation that is only aware of Western markets
        // ToDo: market holidays...
        return if (evaluate.dayOfWeek == DayOfWeek.SUNDAY) {
            false
        } else {
            evaluate.dayOfWeek != DayOfWeek.SATURDAY
        }
    }
}
