package com.beancounter.marketdata.config

import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka

/**
 * Kafka related properties and beans.
 */
@EnableKafka
@ConditionalOnProperty(value = ["kafka.enabled"], matchIfMissing = true)
@Configuration
class KafkaConfig {
    @Value("\${beancounter.topics.trn.csv:bc-trn-csv-dev}")
    lateinit var topicTrnCsvName: String

    @Value("\${beancounter.topics.trn.event:bc-trn-event-dev}")
    lateinit var topicTrnEventName: String

    @Value("\${beancounter.topics.price:bc-price-dev}")
    lateinit var topicPriceName: String

    @Bean
    fun topicTrnCvs(): NewTopic {
        return NewTopic(topicTrnCsvName, 1, 1.toShort())
    }

    @Bean
    fun topicTrnEvent(): NewTopic {
        return NewTopic(topicTrnEventName, 1, 1.toShort())
    }

    @Bean
    fun trnCsvTopic(): String? {
        log.info("BEANCOUNTER_TOPICS_TRN_CSV: {}", topicTrnCsvName)
        return topicTrnCsvName
    }

    @Bean
    fun trnEventTopic(): String? {
        log.info("BEANCOUNTER_TOPICS_TRN_EVENT: {}", topicTrnEventName)
        return topicTrnEventName
    }

    @Bean
    fun topicPrice(kafaConfig: KafkaConfig): NewTopic {
        return NewTopic(topicPriceName, 1, 1.toShort())
    }

    @Bean
    fun priceTopic(): String {
        log.info("BEANCOUNTER_TOPICS_PRICE: {}", topicPriceName)
        return topicPriceName
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaConfig::class.java)
    }
}
