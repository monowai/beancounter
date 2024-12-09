package com.beancounter.event.service

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Schedule properties for triggering corporate actions.
 */
@EnableScheduling
@Configuration
class EventScheduleConfig(
    val dateUtils: DateUtils
) {
    @Bean
    fun scheduleZone(): String {
        log.info(
            "BEANCOUNTER_ZONE: {}",
            dateUtils.zoneId.id
        )
        return dateUtils.zoneId.id
    }

    @Bean
    fun eventsSchedule(
        @Value("\${events.schedule:0 0/15 6-9 * * Tue-Sat}") schedule: String
    ): String {
        log.info(
            "EVENT_SCHEDULE: {}, ZONE: {}",
            schedule,
            dateUtils.zoneId.id
        )
        return schedule
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventScheduleConfig::class.java)
    }
}