package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.DateUtils
import io.sentry.spring.jakarta.tracing.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/**
 * Scheduled event execution to obtain corporate events.
 */
@Service
class EventSchedule(
    private val eventService: EventService,
    private val dateUtils: DateUtils
) {
    private var loginService: LoginService? = null
    private val log = LoggerFactory.getLogger(EventSchedule::class.java)

    @Autowired(required = false)
    fun setLoginService(loginService: LoginService?) {
        this.loginService = loginService
    }

    @SentryTransaction(operation = "scheduled", name = "EventSchedule.processEventsForRange")
    @Scheduled(
        cron = "#{@eventsSchedule}",
        zone = "#{@scheduleZone}"
    )
    fun processEventsForRange() {
        val currentLoginService = loginService
        if (currentLoginService == null) {
            log.warn("LoginService not available, skipping event processing")
            return
        }

        // Simple approach: wrap entire operation in retry logic
        currentLoginService.retryOnJwtExpiry {
            // Authenticate
            currentLoginService.setAuthContext(currentLoginService.loginM2m())

            // Process events
            val end = dateUtils.date
            val start = end.minusDays(5)

            log.debug("Processing events from $start to $end")

            val events = eventService.findInRange(start, end)

            events.forEach { event ->
                eventService.processEvent(event)
            }

            if (events.isEmpty()) {
                log.info("No corporate events to process")
            } else {
                log.info("Successfully processed {} corporate events", events.size)
            }
        }
    }
}