package com.beancounter.common.telemetry

import io.micrometer.observation.ObservationPredicate
import io.micrometer.observation.ObservationRegistry
import io.micrometer.tracing.Tracer
import io.micrometer.tracing.handler.DefaultTracingObservationHandler
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext
import io.micrometer.tracing.otel.bridge.OtelTracer
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.sentry.spring7.SentryTaskDecorator
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.SmartInitializingSingleton
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
     * Provide the Micrometer [Tracer] that bridges observations to the agent's
     * OpenTelemetry SDK.
     *
     * Under the Sentry OTel javaagent, Spring Boot never builds a Micrometer
     * `Tracer` bean (its own OTel-tracing auto-config does not fire in agent
     * mode). Without it, `ChatObservationAutoConfiguration`'s
     * `TracerPresentObservationConfiguration` backs off — verified live via
     * `/actuator/conditions`: *"@ConditionalOnBean io.micrometer.tracing.Tracer
     * did not find any beans"* — so Spring AI's `gen_ai.client.operation`
     * observation only reaches the **meter** handler (a metric) and never
     * becomes a span. Supplying an [OtelTracer] over the agent's OpenTelemetry
     * registers the tracing observation handler, so `gen_ai.*` spans (with token
     * usage + model) flow to Sentry. HTTP observations are still suppressed by
     * [suppressHttpObservationsHandledByAgent] so the agent stays the sole HTTP
     * span source.
     */
    @Bean
    @ConditionalOnMissingBean(Tracer::class)
    fun micrometerTracer(openTelemetry: OpenTelemetry): Tracer =
        OtelTracer(
            openTelemetry.getTracer("bc-micrometer-tracing"),
            OtelCurrentTraceContext(),
            OtelTracer.EventPublisher { }
        )

    /**
     * Register the span-producing tracing observation handler on the registry.
     *
     * Supplying a [Tracer] bean ([micrometerTracer]) flips Spring AI's
     * `TracerPresentObservationConfiguration` to matched, but under the javaagent
     * Boot's own tracing auto-config stays dormant, so **no tracing
     * `ObservationHandlerGroup` is contributed** — verified live: the registry
     * carried only meter handlers, so `gen_ai.client.operation` was still only a
     * metric. Add [DefaultTracingObservationHandler] directly to the registry
     * (bypasses the missing group) so each observation becomes a span on the
     * agent's OpenTelemetry, parented under the agent's current transaction.
     * `http.*` observations are already filtered out
     * by [suppressHttpObservationsHandledByAgent], so only `gen_ai.*` and other
     * non-HTTP observations are traced — no duplication of the agent's HTTP spans.
     */
    @Bean
    fun tracingObservationHandlerInitializer(
        observationRegistry: ObjectProvider<ObservationRegistry>,
        tracer: Tracer
    ): SmartInitializingSingleton =
        SmartInitializingSingleton {
            // Add the handler after all singletons exist — injecting the registry
            // directly would deadlock (Boot wires handler beans into the registry
            // while it is still being created). ObjectProvider defers the lookup.
            observationRegistry.ifAvailable
                ?.observationConfig()
                ?.observationHandler(DefaultTracingObservationHandler(tracer))
        }

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