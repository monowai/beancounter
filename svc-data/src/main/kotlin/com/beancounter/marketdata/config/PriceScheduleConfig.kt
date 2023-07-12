package com.beancounter.marketdata.config

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Initialise the beans required for PriceSchedule execution to work. Failed to init when located in PriceSchedule
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(value = ["schedule.enabled"], havingValue = "true", matchIfMissing = false)
class PriceScheduleConfig(val dateUtils: DateUtils) {

    companion object {
        private val log = LoggerFactory.getLogger(PriceScheduleConfig::class.java)
    }

    @Bean
    fun scheduleZone(): String {
        log.debug("Schedule zone {}, zoneId: {}", dateUtils.defaultZone, dateUtils.getZoneId().id)
        return dateUtils.defaultZone
    }

    @Bean
    fun assetsSchedule(@Value("\${assets.schedule:0 0/15 7-18 * * Tue-Sat}") schedule: String): String {
        log.info("ASSETS_SCHEDULE: {}, BEANCOUNTER_ZONE: {}", schedule, dateUtils.defaultZone)
        return schedule
    }
}
