package com.beancounter.event.integration

import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class EventReceiverConfig {
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

    companion object {
        private val log = LoggerFactory.getLogger(EventReceiverConfig::class.java)
    }
}
