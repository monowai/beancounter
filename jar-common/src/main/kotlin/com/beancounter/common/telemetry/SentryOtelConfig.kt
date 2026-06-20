package com.beancounter.common.telemetry

import io.micrometer.observation.ObservationPredicate
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.sentry.spring7.SentryTaskDecorator
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
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

    /**
     * Bridge Micrometer tracing onto the **agent's** OpenTelemetry pipeline.
     *
     * The Sentry OTel javaagent installs the global [OpenTelemetry] (with the
     * Sentry span processor) and ships the spans it auto-instruments — but Spring
     * Boot's `micrometer-tracing-bridge-otel` otherwise builds its **own** OTel SDK
     * with no exporter, so Spring AI's `gen_ai.client.operation` observations
     * (and any other Micrometer observation) never reach Sentry. Exposing
     * [GlobalOpenTelemetry] as the `OpenTelemetry` bean makes the bridge's
     * `OtelTracer` use the agent's tracer, so those observations export to Sentry.
     *
     * Without the agent attached (local dev / tests) `GlobalOpenTelemetry.get()`
     * is a no-op instance, so Micrometer tracing is simply inert — matching the
     * existing "no agent, no tracing" behaviour.
     */
    @Bean
    @ConditionalOnMissingBean(OpenTelemetry::class)
    fun openTelemetry(): OpenTelemetry = GlobalOpenTelemetry.get()

    /**
     * Suppress Spring's Micrometer HTTP observations so they don't duplicate the
     * agent's spans once Micrometer tracing is bridged to the agent pipeline.
     *
     * The agent already instruments HTTP server + client (spans + trace-context
     * propagation). If the matching Micrometer observations also produced spans we
     * would get a duplicate `http.server` transaction and duplicate `http.client`
     * spans — the same double-instrumentation seen in bc-view. `gen_ai.*` and all
     * non-HTTP observations are unaffected and keep flowing to Sentry.
     */
    @Bean
    fun suppressHttpObservationsHandledByAgent(): ObservationPredicate =
        ObservationPredicate { name, _ ->
            name != "http.server.requests" && name != "http.client.requests"
        }

    @PreDestroy
    fun logShutdown() {
        log.debug("Sentry configuration shutting down")
    }
}