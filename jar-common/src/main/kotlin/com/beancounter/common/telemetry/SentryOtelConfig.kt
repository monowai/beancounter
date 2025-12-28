package com.beancounter.common.telemetry

import io.sentry.spring.jakarta.SentryTaskDecorator
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Sentry Configuration for Spring Boot.
 *
 * Uses Spring Boot auto-configuration via sentry-spring-boot-starter-jakarta.
 * The Sentry OpenTelemetry agent handles SDK initialization when deployed.
 *
 * Configuration is done via application.yml:
 * - sentry.dsn: The Sentry DSN
 * - sentry.environment: Environment name
 * - sentry.traces-sample-rate: Trace sampling rate
 * - sentry.enabled: Enable/disable Sentry
 */
@ConditionalOnProperty(
    name = ["sentry.enabled"],
    havingValue = "true"
)
@Configuration
class SentryOtelConfig {
    private val log = LoggerFactory.getLogger(SentryOtelConfig::class.java)

    init {
        log.info("Sentry integration enabled - using Spring Boot auto-configuration")
    }

    /**
     * Enables async task tracing with Sentry.
     */
    @Bean
    fun sentryTaskDecorator() = SentryTaskDecorator()

    @PreDestroy
    fun logShutdown() {
        log.debug("Sentry configuration shutting down")
    }
}