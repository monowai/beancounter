package com.beancounter.marketdata.config

import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import javax.annotation.PostConstruct

@EnableScheduling
@EnableAsync
@Configuration
class ScheduleConfig(val dateUtils: DateUtils) {
    @PostConstruct
    fun logStatus() {
        log.info("Scheduling enabled")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScheduleConfig::class.java)
    }

    @Bean
    fun scheduleZone(): String {
        log.info("SCHEDULE_ZONE: {}", dateUtils.defaultZone)
        return dateUtils.defaultZone
    }

}
