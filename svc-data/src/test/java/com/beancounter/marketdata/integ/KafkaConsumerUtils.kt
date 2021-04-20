package com.beancounter.marketdata.integ

import org.apache.kafka.clients.consumer.Consumer
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.test.EmbeddedKafkaBroker
import org.springframework.kafka.test.utils.KafkaTestUtils

/**
 * Common Kafka client utils.
 */
class KafkaConsumerUtils {
    private val consumers: MutableMap<String, Consumer<String, String>> = HashMap()
    fun getConsumer(group: String, topic: String, broker: EmbeddedKafkaBroker): Consumer<String, String> {
        var consumer = consumers[group]
        if (consumer == null) {
            val consumerProps = KafkaTestUtils.consumerProps(group, "false", broker)
            consumerProps["session.timeout.ms"] = 6000
            consumerProps[ConsumerConfig.AUTO_OFFSET_RESET_CONFIG] = "earliest"
            val cf = DefaultKafkaConsumerFactory<String, String>(consumerProps)
            consumer = cf.createConsumer()
            consumers[group] = consumer
            broker.consumeFromEmbeddedTopics(consumer, topic)
        }
        return consumer!!
    }
}
