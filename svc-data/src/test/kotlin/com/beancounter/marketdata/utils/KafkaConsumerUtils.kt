package com.beancounter.marketdata.utils

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils

/**
 * Common Kafka client utils.
 */
class KafkaConsumerUtils {
    fun getConsumer(group: String, topic: String, broker: EmbeddedKafkaBroker): Consumer<String, String> {
        val consumerProps = KafkaTestUtils.consumerProps(group, "true", broker)
        consumerProps[ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG] = 3000
        consumerProps[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG] = 2000
        consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
        val cf = DefaultKafkaConsumerFactory<String, String>(consumerProps)
        val consumer = cf.createConsumer()
        broker.consumeFromEmbeddedTopics(consumer, topic)
        return consumer
    }
}
