package com.beancounter.marketdata.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Market-close-aligned price refresh triggers.
 *
 * Each [MarketClose] fires the idempotent price refresh shortly after a
 * market's end-of-day prices publish, expressed in that market's OWN
 * timezone. Spring's named-zone [org.springframework.scheduling.support.CronTrigger]
 * tracks daylight-saving transitions automatically, so a London/New-York
 * close stays aligned year round.
 *
 * Because the refresh only fetches assets that still lack today's price and
 * the per-market availability gate ([com.beancounter.common.utils.PreviousClosePriceDate])
 * resolves the correct close date per market, a fire only pulls the markets
 * that have actually closed and published — the UK fire skips US assets, and
 * vice-versa.
 *
 * Override via `beancounter.market.schedule.closes` (env / yaml).
 */
@ConfigurationProperties(prefix = "beancounter.market.schedule")
class PriceScheduleProperties {
    var closes: MutableList<MarketClose> =
        mutableListOf(
            // LSE closes 16:30 Europe/London; EODHD publishes EOD ~2-3h later.
            // Fires on the close day (MON-FRI) in London's own zone.
            MarketClose(
                name = "UK",
                cron = "0 30 19 * * MON-FRI",
                zone = "Europe/London"
            ),
            // NYSE/NASDAQ close 16:00 America/New_York; EODHD majors ~15m later.
            // Fires on the close day (MON-FRI) in New York's own zone.
            MarketClose(
                name = "US",
                cron = "0 0 17 * * MON-FRI",
                zone = "America/New_York"
            )
        )
}

/**
 * A single close-aligned refresh trigger.
 *
 * @param name  human label for logs/metrics (e.g. "UK", "US")
 * @param cron  Spring 6-field cron expression (sec min hour dom mon dow)
 * @param zone  IANA timezone id the cron is evaluated in (e.g. "Europe/London")
 */
data class MarketClose(
    var name: String = "",
    var cron: String = "",
    var zone: String = ""
)