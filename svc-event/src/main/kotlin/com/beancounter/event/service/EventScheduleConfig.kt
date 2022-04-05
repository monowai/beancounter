package com.beancounter.event.service

import com.beancounter.common.contracts.PriceRequest.Companion.dateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

@EnableScheduling
@Configuration
class EventScheduleConfig {

    @Bean
    fun scheduleZone(): String {
        log.info("BEANCOUNTER_ZONE: {}", dateUtils.defaultZone)
        return dateUtils.defaultZone
    }

    @Bean
    fun eventsSchedule(@Value("\${events.schedule:0 0/15 6-9 * * Tue-Sat}") schedule: String): String {
        log.info("EVENT_SCHEDULE: {}, ZONE: {}", schedule, dateUtils.defaultZone)
        return schedule
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventScheduleConfig::class.java)
    }
}
