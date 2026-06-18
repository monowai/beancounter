package com.beancounter.marketdata.config

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Initialise the beans required for PriceSchedule execution to work. Failed to init when located in PriceSchedule
 */
@Configuration
@EnableScheduling
@EnableConfigurationProperties(PriceScheduleProperties::class)
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PriceScheduleConfig(
    val dateUtils: DateUtils
) {
    companion object {
        private val log = LoggerFactory.getLogger(PriceScheduleConfig::class.java)
    }

    // Shared default zone for the annotation-based @Scheduled jobs
    // (FX, classification, auto-settle, news retention). The market-close
    // price refresh no longer uses this — it registers per-market named-zone
    // cron triggers via PriceScheduleProperties / PriceSchedule.
    @Bean
    fun scheduleZone(): String = dateUtils.zoneId.id

    /**
     * Cron expression for the FX rate pre-fetch job. Default: 17:00 every
     * weekday — after ECB publishes daily rates (~16:00 CET). Overridable via
     * `beancounter.fx.schedule.cron`. The schedule warms the DB cache for
     * the current day so user-driven corporate-action events on the next
     * trading session don't pay the 600ms+ cold-cache external API call.
     */
    @Bean
    fun fxRateScheduleCron(
        @Value("\${beancounter.fx.schedule.cron:0 0 17 * * MON-FRI}") schedule: String
    ): String {
        log.info(
            "FX_RATE_SCHEDULE: {}, BEANCOUNTER_ZONE: {}",
            schedule,
            dateUtils.zoneId.id
        )
        return schedule
    }
}