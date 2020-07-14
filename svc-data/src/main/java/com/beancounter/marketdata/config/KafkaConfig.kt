package com.beancounter.marketdata.config

import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka

@EnableKafka
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
@Configuration
class KafkaConfig {
    @Value("\${beancounter.topics.trn.csv:bc-trn-csv-dev}")
    var topicTrnCsv: String? = null

    @Value("\${beancounter.topics.trn.event:bc-trn-event-dev}")
    var topicTrnEvent: String? = null

    @Bean
    fun topicTrnCvs(): NewTopic {
        return NewTopic(topicTrnCsv, 1, 1.toShort())
    }

    @Bean
    fun topicTrnEvent(): NewTopic {
        return NewTopic(topicTrnEvent, 1, 1.toShort())
    }

    @Bean
    fun trnCsvTopic(): String? {
        log.info("BEANCOUNTER_TOPICS_TRN_CSV: {}", topicTrnCsv)
        return topicTrnCsv
    }

    @Bean
    fun trnEventTopic(): String? {
        log.info("BEANCOUNTER_TOPICS_TRN_EVENT: {}", topicTrnEvent)
        return topicTrnEvent
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaConfig::class.java)
    }
}