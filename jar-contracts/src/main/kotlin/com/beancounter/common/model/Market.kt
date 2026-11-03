package com.beancounter.common.model

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonInclude
import java.math.BigDecimal
import java.time.LocalTime
import java.util.TimeZone

private const val DEFAULT_OPEN_HOUR = 9
private const val DEFAULT_OPEN_MINUTE = 30
private const val DEFAULT_PRICE_HOUR = 19

/**
 * A stock exchange.
 *
 * @author mikeh
 * @since 2019-01-27
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Market(
    val code: String,
    val name: String = code,
    val currencyId: String = "USD",
    val timezoneId: String = "US/Eastern",
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val aliases: Map<String, String> = mapOf(),
    val timezone: TimeZone = TimeZone.getTimeZone(timezoneId),
    @Transient
    var currency: Currency = Currency(currencyId),
    /** When trading opens on the market's own clock. Live quotes are asked for from here. */
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "HH:mm"
    )
    val openTime: LocalTime =
        LocalTime.of(
            DEFAULT_OPEN_HOUR,
            DEFAULT_OPEN_MINUTE
        ),
    @JsonFormat(
        shape = JsonFormat.Shape.STRING,
        pattern = "HH:mm"
    )
    val priceTime: LocalTime =
        LocalTime.of(
            DEFAULT_PRICE_HOUR,
            0
        ),
    val daysToSubtract: Int = 1,
    val enricher: String? = null,
    val multiplier: BigDecimal = BigDecimal("1.0"),
    val active: Boolean = true
) {
    fun getAlias(market: String): String? = aliases[market.lowercase()]
}