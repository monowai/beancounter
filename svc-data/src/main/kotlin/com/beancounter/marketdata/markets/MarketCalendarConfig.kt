package com.beancounter.marketdata.markets

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Component
import java.time.LocalDate

/**
 * Market Object Management Service.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.calendar")
@Component
class MarketCalendarConfig
    @Autowired
    constructor(
        val values: List<MarketHolidayAnnual>
    ) {
        private val map: MutableMap<String, List<LocalDate>> = mutableMapOf()

        @Cacheable("market.holidays")
        fun marketHolidays(
            year: Int = LocalDate.now().year,
            market: String
        ): List<LocalDate> =
            map.getOrPut(market) {
                buildMarketHolidays(
                    year,
                    market
                )
            }

        fun buildMarketHolidays(
            year: Int = LocalDate.now().year,
            market: String
        ): List<LocalDate> {
            return values
                .filter { value ->
                    value.markets.contains(market) &&
                        (value.year == "*" || value.year == year.toString())
                }.map { value ->
                    val computedYear = if (value.year == "*") year else value.year.toInt()
                    LocalDate.of(
                        computedYear,
                        value.month.toInt(),
                        value.day
                    )
                }.sorted() // Sort for consistency and easier usage
        }
    }