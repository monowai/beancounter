package com.beancounter.event.integration

import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.config.TopicBuilder

/**
 * Configuration properties for Corporate Actions.
 */
@Configuration
class EventReceiverConfig {
    @Value($$"${beancounter.topics.ca.event:bc-ca-event-dev}")
    lateinit var topicCaEvent: String

    @Bean
    fun topicEvent(): NewTopic =
        TopicBuilder
            .name(topicCaEvent)
            .partitions(1)
            .replicas(1)
            .compact()
            .build()
            .also {
                log.info(
                    "topics.ca.event: {}",
                    topicCaEvent
                )
            }

    @Bean
    fun caTopic(): String? {
        log.info(
            "CA-EVENT: {} ",
            topicCaEvent
        )
        return topicCaEvent
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventReceiverConfig::class.java)
    }
}