package com.beancounter.common.utils

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.kafka.listener.KafkaListenerErrorHandler
import org.springframework.kafka.listener.ListenerExecutionFailedException
import org.springframework.stereotype.Service

/**
 * Helpers to extract nested exceptions from Kafka exceptions.
 */
@Service
@ConditionalOnProperty(value = ["kafka.enabled"])
class KafkaUtils {
    /**
     * Finds the first occurrence of a class from 'com.beancounter' in the exception stack trace.
     *
     * @param e The ListenerExecutionFailedException to analyze.
     * @return The string representation of the first 'com.beancounter' stack trace element, or
     *         "No BC Classes Found" if no such element exists.
     */
    fun findBcCause(e: ListenerExecutionFailedException): String =
        e.mostSpecificCause.stackTrace
            .firstOrNull { it.className.contains("com.beancounter") }
            ?.toString()
            ?: "No BC Classes Found"

    /**
     * Provides a custom error handler for Kafka listeners to log detailed error information.
     *
     * @return a KafkaListenerErrorHandler that logs the most specific cause of the error,
     *         the relevant BeanCounter class involved, and the message payload that caused the error.
     */
    @Bean
    fun bcErrorHandler(): KafkaListenerErrorHandler =
        KafkaListenerErrorHandler { message, exception ->
            val cause = exception.mostSpecificCause.message ?: "Unknown Error"
            val bcCause = findBcCause(exception)
            val payload = message.payload

            log.error(
                "Error handling Kafka message. Cause: $cause, BC Class: $bcCause, Payload: $payload",
            )

            // Consider adding error metrics or alerts here if needed
        }

    companion object {
        private val log = LoggerFactory.getLogger(KafkaUtils::class.java)
    }
}
