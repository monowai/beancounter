package com.beancounter.event.integration

import com.beancounter.auth.client.LoginService
import com.beancounter.common.input.TrustedEventInput
import com.beancounter.event.service.EventService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

/**
 * Spring Cloud Stream functional consumer for corporate action events.
 * Processes events and publishes resulting transactions via EventPublisher.
 */
@Configuration
class EventStreamConsumer(
    private val eventService: EventService
) {
    private var loginService: LoginService? = null
    private val log = LoggerFactory.getLogger(EventStreamConsumer::class.java)

    @Autowired(required = false)
    fun setLoginService(loginService: LoginService?) {
        this.loginService = loginService
    }

    @Bean
    fun eventProcessor(): Consumer<TrustedEventInput> =
        Consumer { eventInput ->
            log.trace("Processing corporate action event")
            val currentLoginService = loginService
            if (currentLoginService != null) {
                // Wrap in retry logic for M2M token expiry
                currentLoginService.retryOnJwtExpiry {
                    currentLoginService.setAuthContext(currentLoginService.loginM2m())
                    eventService.process(eventInput)
                }
            } else {
                // Auth disabled - process directly
                eventService.process(eventInput)
            }
        }
}