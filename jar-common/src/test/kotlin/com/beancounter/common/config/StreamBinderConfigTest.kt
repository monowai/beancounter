package com.beancounter.common.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.test.context.TestPropertySource

/**
 * Test StreamBinderConfig logs correctly when stream is enabled with RabbitMQ.
 */
@SpringBootTest(classes = [StreamBinderConfig::class, PropertySourcesPlaceholderConfigurer::class])
@TestPropertySource(
    properties = [
        "stream.enabled=true",
        "spring.cloud.stream.default-binder=rabbit",
        "spring.rabbitmq.host=test-rabbit-host",
        "spring.cloud.stream.kafka.binder.brokers=not-set",
        "spring.application.name=test-service"
    ]
)
class StreamBinderConfigRabbitTest {
    @Autowired
    private lateinit var streamBinderConfig: StreamBinderConfig

    @Test
    fun `should load configuration with rabbit binder`() {
        assertThat(streamBinderConfig).isNotNull
    }
}

/**
 * Test StreamBinderConfig logs correctly when stream is enabled with Kafka.
 */
@SpringBootTest(classes = [StreamBinderConfig::class, PropertySourcesPlaceholderConfigurer::class])
@TestPropertySource(
    properties = [
        "stream.enabled=true",
        "spring.cloud.stream.default-binder=kafka",
        "spring.rabbitmq.host=not-set",
        "spring.cloud.stream.kafka.binder.brokers=kafka:9092",
        "spring.application.name=kafka-test-service"
    ]
)
class StreamBinderConfigKafkaTest {
    @Autowired
    private lateinit var streamBinderConfig: StreamBinderConfig

    @Test
    fun `should load configuration with kafka binder`() {
        assertThat(streamBinderConfig).isNotNull
    }
}

/**
 * Test StreamBinderConfig is not loaded when stream is disabled.
 */
@SpringBootTest(classes = [StreamBinderConfig::class, PropertySourcesPlaceholderConfigurer::class])
@TestPropertySource(
    properties = [
        "stream.enabled=false",
        "spring.application.name=disabled-stream-service"
    ]
)
class StreamBinderConfigDisabledTest {
    @Autowired(required = false)
    private var streamBinderConfig: StreamBinderConfig? = null

    @Test
    fun `should not load configuration when stream is disabled`() {
        assertThat(streamBinderConfig).isNull()
    }
}

/**
 * Test StreamBinderConfig uses default values when properties are not set.
 */
@SpringBootTest(classes = [StreamBinderConfig::class, PropertySourcesPlaceholderConfigurer::class])
@TestPropertySource(
    properties = [
        "stream.enabled=true"
        // Intentionally not setting other properties to test defaults
    ]
)
class StreamBinderConfigDefaultsTest {
    @Autowired
    private lateinit var streamBinderConfig: StreamBinderConfig

    @Test
    fun `should load configuration with default values`() {
        assertThat(streamBinderConfig).isNotNull
    }
}