package com.beancounter.event.integration

import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.event.service.EventService
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Controller

/**
 * Responds to Kafka events.
 */
@ConditionalOnProperty(
    value = ["kafka.enabled"],
    matchIfMissing = true,
)
@Controller
class EventReceiver(
    private val eventService: EventService,
) {
    @KafkaListener(
        topics = ["#{@caTopic}"],
        errorHandler = "bcErrorHandler",
    )
    fun processMessage(eventRequest: TrustedEventInput): Collection<TrustedTrnEvent> =
        eventService.process(eventRequest)
}
