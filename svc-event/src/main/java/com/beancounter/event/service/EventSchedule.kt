package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

@Service
class EventSchedule(private val eventService: EventService) {
    private var loginService: LoginService? = null
    private lateinit var dateUtils: DateUtils

    @Autowired(required = false)
    fun setLoginService(loginService: LoginService?) {
        this.loginService = loginService
    }

    @Bean
    fun eventsSchedule (@Value("\${events.schedule:0 */30 7-18 ? * Mon-Sat}") schedule: String, dateUtils: DateUtils): String {
        this.dateUtils = dateUtils
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
        val start = end!!.minusDays(5)
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