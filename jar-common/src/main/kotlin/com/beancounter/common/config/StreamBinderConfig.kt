package com.beancounter.common.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration

/**
 * Logs Spring Cloud Stream binder configuration at startup.
 */
@Configuration
@ConditionalOnProperty(
    value = ["stream.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class StreamBinderConfig(
    @Value($$"${spring.cloud.stream.default-binder:not-set}")
    private val defaultBinder: String,
    @Value($$"${spring.rabbitmq.host:not-set}")
    private val rabbitHost: String,
    @Value($$"${spring.cloud.stream.kafka.binder.brokers:not-set}")
    private val kafkaBrokers: String,
    @Value($$"${spring.application.name:unknown}")
    private val applicationName: String
) {
    private val log = LoggerFactory.getLogger(StreamBinderConfig::class.java)

    @PostConstruct
    fun logBinderConfiguration() {
        log.info(
            "Stream configuration for {}: default-binder={}, rabbit.host={}, kafka.brokers={}",
            applicationName,
            defaultBinder,
            rabbitHost,
            kafkaBrokers
        )
    }
}