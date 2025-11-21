package com.beancounter.event.integration

import com.beancounter.common.input.TrustedEventInput
import com.beancounter.event.service.EventService
import org.slf4j.LoggerFactory
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
    private val log = LoggerFactory.getLogger(EventStreamConsumer::class.java)

    @Bean
    fun eventProcessor(): Consumer<TrustedEventInput> =
        Consumer { eventInput ->
            log.trace("Processing corporate action event")
            eventService.process(eventInput)
        }
}