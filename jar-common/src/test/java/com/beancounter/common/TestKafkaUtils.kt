package com.beancounter.common

import com.beancounter.common.utils.KafkaUtils
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.messaging.Message
import org.springframework.messaging.support.ErrorMessage

class TestKafkaUtils {
    @Test
    fun is_ErrorCovered() {
        val kafkaUtils = KafkaUtils()
        val failedException = ListenerExecutionFailedException("Blah")
        val result = kafkaUtils.findBcCause(failedException)
        Assertions.assertThat(result.contains("com.beancounter"))
        val handler = kafkaUtils.bcErrorHandler()
        val message: Message<*> = ErrorMessage(failedException)
        val errorResult = handler.handleError(message, failedException)
        Assertions.assertThat(errorResult).isNull()
    }
}
