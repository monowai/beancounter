package com.beancounter.common.utils

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.messaging.Message
import org.springframework.messaging.support.ErrorMessage

/**
 * Can we interpret BC exceptions from Kafka Exceptions?
 */
class KafkaUtilsTest {
    @Test
    fun is_ErrorCovered() {
        val kafkaUtils = KafkaUtils()
        val failedException = ListenerExecutionFailedException("Blah")
        val result = kafkaUtils.findBcCause(failedException)
        assertThat(result.contains("com.beancounter"))
        val handler = kafkaUtils.bcErrorHandler()
        val message: Message<*> = ErrorMessage(failedException)
        val errorResult =
            handler.handleError(
                message,
                failedException
            )
        assertThat(errorResult).isNotNull
    }
}