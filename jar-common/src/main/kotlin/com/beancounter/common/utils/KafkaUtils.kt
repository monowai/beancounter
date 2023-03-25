package com.beancounter.common.utils

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.kafka.listener.KafkaListenerErrorHandler
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.messaging.Message
import org.springframework.stereotype.Service

/**
 * Helpers to extract nested exceptions from Kafka exceptions.
 */
@Service
@ConditionalOnProperty(value = ["kafka.enabled"])
class KafkaUtils {
    fun findBcCause(e: ListenerExecutionFailedException): String {
        val stackTrace = e.mostSpecificCause.stackTrace
        for (stackTraceElement in stackTrace) {
            if (stackTraceElement.className.contains("com.beancounter")) {
                return stackTraceElement.toString()
            }
        }
        return "No BC Classes Found"
    }

    @Bean
    fun bcErrorHandler(): KafkaListenerErrorHandler {
        return KafkaListenerErrorHandler { m: Message<*>, e: ListenerExecutionFailedException ->
            log.error(
                "{}. {}. {}",
                e.mostSpecificCause.message,
                findBcCause(e),
                m.payload,
            )
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaUtils::class.java)
    }
}
