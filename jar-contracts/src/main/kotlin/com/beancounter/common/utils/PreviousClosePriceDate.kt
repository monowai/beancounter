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
    private val dateUtils: DateUtils,
    private val marketHolidays: MarketHolidays = MarketHolidays()
) {
    companion object {
        val log: Logger = LoggerFactory.getLogger(this::class.java)
    }

    fun getPriceDate(
        requestDateTime: ZonedDateTime,
        market: Market,
        isCurrent: Boolean = dateUtils.isToday(requestDateTime.toLocalDate().toString())
    ): LocalDate =
        if (isCurrent) {
            // Re-express "now" on the market's own wall clock so priceTime is
            // honoured in the exchange timezone (DST included), independent of
            // the request's or the server's zone. Both the availability check
            // and the trading-day walk-back then operate in market-local time.
            val marketLocal = requestDateTime.withZoneSameInstant(market.timezone.toZoneId())
            val pricesAvailable =
                getPricesAvailable(
                    requestDateTime,
                    market
                )
            val daysToSubtract =
                getDaysToSubtract(
                    market,
                    marketLocal.toLocalDateTime(),
                    pricesAvailable.toLocalDateTime()
                )

            log.trace(
                "request: $requestDateTime, subtract: $daysToSubtract, marketLocal: $marketLocal, " +
                    "marketCloses: $pricesAvailable, timezone: ${market.timezoneId}"
            )
            getPriceDate(
                marketLocal,
                daysToSubtract,
                market
            )
        } else {
            // Just account for work days
            log.trace("Returning last working day relative to requested date")
            getPriceDate(
                requestDateTime,
                0,
                market
            )
        }

    /**
     * When today's close price becomes available, expressed on the market's
     * local clock: the requested instant projected into the market timezone,
     * at the market's configured [Market.priceTime].
     */
    fun getPricesAvailable(
        requestDateTime: ZonedDateTime,
        market: Market
    ): OffsetDateTime {
        val marketLocal = requestDateTime.withZoneSameInstant(market.timezone.toZoneId())
        return OffsetDateTime.of(
            marketLocal.toLocalDate(),
            market.priceTime,
            marketLocal.offset
        )
    }

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
        daysToSubtract: Int,
        market: Market? = null
    ): LocalDate {
        var notionalDateTime = seedDate.minusDays(daysToSubtract.toLong())
        while (!isTradingDay(notionalDateTime, market)) {
            notionalDateTime = notionalDateTime.minusDays(1)
        }
        return notionalDateTime.toLocalDate()
    }

    fun isTradingDay(
        dateTime: ZonedDateTime,
        market: Market? = null
    ): Boolean {
        val dayOfWeek = dateTime.dayOfWeek

        // Check for weekends
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            return false
        }

        // Holidays resolve against the market's own exchange calendar
        // (London → UK, otherwise US). A null market keeps the US default.
        if (marketHolidays.isHoliday(dateTime.toLocalDate(), market?.timezone?.toZoneId())) {
            return false
        }

        return true
    }
}