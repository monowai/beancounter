package com.beancounter.position.schedule

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling

/**
 * Configuration for scheduled portfolio valuation tasks.
 * Enabled when schedule.enabled=true.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(
    value = ["schedule.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PositionScheduleConfig(
    val dateUtils: DateUtils
) {
    companion object {
        private val log = LoggerFactory.getLogger(PositionScheduleConfig::class.java)
    }

    @Bean
    fun scheduleZone(): String = dateUtils.zoneId.id

    @Bean
    fun valuationSchedule(
        @Value("\${valuation.schedule:0 0/10 5-7 * * Tue-Sat}") schedule: String
    ): String {
        log.info(
            "VALUATION_SCHEDULE: {}, BEANCOUNTER_ZONE: {}",
            schedule,
            dateUtils.zoneId.id
        )
        return schedule
    }
}