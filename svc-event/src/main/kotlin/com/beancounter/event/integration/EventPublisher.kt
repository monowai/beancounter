package com.beancounter.event.integration

import com.beancounter.common.input.TrustedTrnEvent
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service

/**
 * Publish notification of a corporate action transaction affecting a portfolio
 */
@ConditionalOnProperty(
    value = ["kafka.enabled"],
    matchIfMissing = true,
)
@Service
class EventPublisher {
    @Value("\${beancounter.topics.trn.event:bc-trn-event-dev}")
    lateinit var topicTrnEvent: String
    private lateinit var kafkaTemplate: KafkaTemplate<String, TrustedTrnEvent>

    @PostConstruct
    fun logConfig() {
        log.info(
            "TRN-EVENT: {} ",
            topicTrnEvent,
        )
    }

    @Autowired
    fun setKafkaTemplate(kafkaTemplate: KafkaTemplate<String, TrustedTrnEvent>) {
        this.kafkaTemplate = kafkaTemplate
    }

    fun send(trnEvent: TrustedTrnEvent) {
        kafkaTemplate.send(
            topicTrnEvent,
            trnEvent,
        )
    }

    companion object {
        private val log = LoggerFactory.getLogger(EventPublisher::class.java)
    }
}
