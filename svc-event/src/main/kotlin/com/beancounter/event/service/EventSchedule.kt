package com.beancounter.event.service

import com.beancounter.auth.client.LoginService
import com.beancounter.common.utils.DateUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.security.oauth2.jwt.JwtException
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

        try {
            // Authenticate with JWT token caching and automatic refresh
            currentLoginService.setAuthContext(currentLoginService.loginM2m())

            processEventsWithAuth()
        } catch (jwtException: JwtException) {
            log.warn("JWT authentication failed: ${jwtException.message}, attempting token refresh...")

            // Handle JWT expiry by refreshing token and retrying
            try {
                currentLoginService.handleJwtExpiryAndRetry {
                    processEventsWithAuth()
                }
            } catch (retryException: Exception) {
                log.error("Failed to process events even after token refresh", retryException)
                throw retryException
            }
        } catch (exception: Exception) {
            log.error("Unexpected error during event processing", exception)
            throw exception
        }
    }

    /**
     * Process events with authenticated context
     */
    private fun processEventsWithAuth() {
        val end = dateUtils.date
        val start = end.minusDays(5)

        log.debug("Processing events from $start to $end")

        val events = eventService.findInRange(start, end)

        for (event in events) {
            try {
                eventService.processEvent(event)
            } catch (_: JwtException) {
                log.warn("JWT expired while processing event ${event.id}, refreshing token...")

                // Refresh token and retry this specific event
                val retryLoginService = loginService
                retryLoginService?.handleJwtExpiryAndRetry {
                    eventService.processEvent(event)
                }
            }
        }

        if (events.isEmpty()) {
            log.info("No corporate events to process")
        } else {
            log.info("Successfully processed {} corporate events", events.size)
        }
    }
}