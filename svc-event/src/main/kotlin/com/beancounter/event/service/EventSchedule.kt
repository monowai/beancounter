package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
@EnableScheduling
@EnableAsync
@Configuration
class EventSchedule(private val eventService: EventService, private val dateUtils: DateUtils) {

    private var loginService: LoginService? = null

    @Bean
    fun scheduleZone(): String {
        log.info("BEANCOUNTER_ZONE: {}", dateUtils.defaultZone)
        return dateUtils.defaultZone
    }

    @Autowired(required = false)
    fun setLoginService(loginService: LoginService?) {
        this.loginService = loginService
    }

    @Bean
    fun eventsSchedule(@Value("\${events.schedule:0 0/15 6-9 * * Tue-Sat}") schedule: String): String {
        log.info("EVENT_SCHEDULE: {}, ZONE: {}", schedule, dateUtils.defaultZone)
        return schedule
    }

    @Scheduled(cron = "#{@eventsSchedule}", zone = "#{@scheduleZone}")
    fun processEventsForRange() {
        log.info("Checking for corporate events to process")
        if (loginService != null) {
            loginService!!.login()
        }
        val end = dateUtils.date
        val start = end.minusDays(5)
        val events = eventService.findInRange(start, end)
        for (event in events) {
            eventService.processMessage(event)
        }
        if (!events.isEmpty()) {
            log.info("Processed {} events", events.size)
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventSchedule::class.java)
    }
}
