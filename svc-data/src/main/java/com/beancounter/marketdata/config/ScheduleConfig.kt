package com.beancounter.marketdata.config

import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import javax.annotation.PostConstruct

@EnableScheduling
@EnableAsync
@Configuration
class ScheduleConfig {

    @PostConstruct
    fun logStatus() {
       log.info("Scheduling enabled")
    }

    companion object {
        private val log = LoggerFactory.getLogger(ScheduleConfig::class.java)
    }

}
