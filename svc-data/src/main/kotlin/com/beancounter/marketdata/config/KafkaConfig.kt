package com.beancounter.marketdata.config

import org.apache.kafka.clients.admin.NewTopic
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.TopicBuilder

/**
 * Kafka related properties and beans.
 */
@EnableKafka
@ConditionalOnProperty(
    value = ["kafka.enabled"],
    matchIfMissing = true
)
@Configuration
class KafkaConfig {
    @Value("\${beancounter.topics.pos.mv:bc-pos-mv-dev}")
    lateinit var topicPosMvName: String

    @Value("\${beancounter.topics.trn.csv:bc-trn-csv-dev}")
    lateinit var topicTrnCsvName: String

    @Value("\${beancounter.topics.trn.event:bc-trn-event-dev}")
    lateinit var topicTrnEventName: String

    @Value("\${beancounter.topics.price:bc-price-dev}")
    lateinit var topicPriceName: String
    private val log = LoggerFactory.getLogger(KafkaConfig::class.java)

    @Bean
    fun topicPosMv(): NewTopic =
        TopicBuilder
            .name(topicPosMvName)
            .partitions(1)
            .replicas(1)
            .compact()
            .build()

    @Bean
    fun topicTrnCvs(): NewTopic =
        TopicBuilder
            .name(topicTrnCsvName)
            .partitions(1)
            .replicas(1)
            .compact()
            .build()

    @Bean
    fun topicTrnEvent(): NewTopic =
        TopicBuilder
            .name(topicTrnEventName)
            .partitions(1)
            .replicas(1)
            .compact()
            .build()

    @Bean
    fun topicPrice(kafaConfig: KafkaConfig): NewTopic =
        TopicBuilder
            .name(topicPriceName)
            .partitions(1)
            .replicas(1)
            .compact()
            .build()

    @Bean
    fun trnCsvTopic(): String {
        log.info(
            "BEANCOUNTER_TOPICS_TRN_CSV: {}",
            topicTrnCsvName
        )
        return topicTrnCsvName
    }

    @Bean
    fun trnEventTopic(): String {
        log.info(
            "BEANCOUNTER_TOPICS_TRN_EVENT: {}",
            topicTrnEventName
        )
        return topicTrnEventName
    }

    @Bean
    fun priceTopic(): String {
        log.info(
            "BEANCOUNTER_TOPICS_PRICE: {}",
            topicPriceName
        )
        return topicPriceName
    }

    @Bean
    fun posMvTopic(): String =
        topicPosMvName.also {
            log.info(
                "BEANCOUNTER_TOPICS_POS_MV: {}",
                topicPosMvName
            )
        }
}