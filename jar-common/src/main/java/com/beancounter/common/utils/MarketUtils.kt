package com.beancounter.common.utils

import com.beancounter.common.model.Market
import org.springframework.stereotype.Component
import java.time.*

@Component
class MarketUtils(private val dateUtils: DateUtils) {

    fun getLastMarketDate(date: LocalDate, market: Market): LocalDate {
        return getLastMarketDate(date.atTime(LocalTime.now()), market)
    }

    fun getLastMarketDate(localDateTime: LocalDateTime, // Callers date/time
                          market: Market,
                          isCurrent: Boolean = dateUtils.isToday(dateUtils.getDateString(localDateTime.toLocalDate()))
    ): LocalDate {
        return if (isCurrent) {
            // Resolve if "today" has prices available taking into account all variables
            val zonedLocal = ZonedDateTime.ofLocal(localDateTime, ZoneId.systemDefault(), ZoneOffset.UTC)
            val marketLocal = zonedLocal.withZoneSameInstant(market.timezone.toZoneId())
            val zonedRemote = ZonedDateTime.ofLocal(
                    LocalDateTime.of(marketLocal.toLocalDate(), market.priceTime),
                    market.timezone.toZoneId(),
                    ZoneOffset.UTC)

            var daysToSubtract = market.daysToSubtract
            // Is requested date on or after when prices are available?
            if (zonedLocal >= zonedRemote.withZoneSameInstant(ZoneId.systemDefault())) {
                daysToSubtract = 0
            }
            getLastMarketDate(marketLocal.toLocalDateTime(), daysToSubtract)
        } else {
            // Just account for market open
            getLastMarketDate(localDateTime, 0)
        }

    }

    fun getLastMarketDate(date: LocalDate, daysToSubtract: Int): Any {
        return getLastMarketDate(date.atStartOfDay(), daysToSubtract)
    }

    /**
     * Finds the closest market price date relative to the requested date
     *
     * @param seedDate   usually Today
     * @return resolved Date
     */
    fun getLastMarketDate(seedDate: LocalDateTime, daysToSubtract: Int): LocalDate {
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