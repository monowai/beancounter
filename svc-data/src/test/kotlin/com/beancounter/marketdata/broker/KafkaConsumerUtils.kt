package com.beancounter.marketdata.broker

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
        val consumerProps = KafkaTestUtils.consumerProps(group, "true", broker).apply {
            put(ConsumerConfig.REQUEST_TIMEOUT_MS_CONFIG, 3000)
            put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 2000)
            put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 1)
            put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 5000)
        }

        val consumer = DefaultKafkaConsumerFactory<String, String>(consumerProps).createConsumer()
        broker.consumeFromEmbeddedTopics(consumer, topic)
        return consumer
    }
}
