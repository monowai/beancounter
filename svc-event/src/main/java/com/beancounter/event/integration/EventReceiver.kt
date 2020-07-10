package com.beancounter.event.integration

import com.beancounter.common.input.TrustedEventInput
import com.beancounter.common.input.TrustedTrnEvent
import com.beancounter.event.service.EventService
import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Service

@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
@Service
class EventReceiver(private val eventService: EventService) {

    @Value("\${beancounter.topics.ca.event:bc-ca-event-dev}")
    var topicCaEvent: String? = null

    @Bean
    fun topicEvent(): NewTopic {
        log.info("topics.ca.event: {}", topicCaEvent)
        return NewTopic(topicCaEvent, 1, 1.toShort())
    }

    @Bean
    fun caTopic(): String? {
        log.info("CA-EVENT: {} ", topicCaEvent)
        return topicCaEvent
    }

    @KafkaListener(topics = ["#{@caTopic}"], errorHandler = "bcErrorHandler")
    fun processMessage(eventRequest: TrustedEventInput): Collection<TrustedTrnEvent> {
        return eventService.processMessage(eventRequest)
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventReceiver::class.java)
    }

}