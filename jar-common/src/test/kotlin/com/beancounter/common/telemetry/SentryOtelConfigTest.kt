package com.beancounter.common.telemetry

import io.micrometer.observation.Observation
import io.micrometer.observation.ObservationPredicate
import io.opentelemetry.api.OpenTelemetry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

/**
 * Test SentryConfig is wired when enabled.
 */
@SpringBootTest(classes = [SentryOtelConfig::class])
@TestPropertySource(
    properties = [
        "sentry.dsn=https://test@test.ingest.sentry.io/123",
        "sentry.environment=test-env",
        "sentry.enabled=true"
    ]
)
class SentryOtelConfigTest {
    @Autowired
    private lateinit var sentryOtelConfig: SentryOtelConfig

    @Test
    fun `should wire SentryOtelConfig when enabled`() {
        assertThat(sentryOtelConfig).isNotNull
    }

    @Test
    fun `should provide SentryTaskDecorator bean`() {
        assertThat(sentryOtelConfig.sentryTaskDecorator()).isNotNull
    }

    @Test
    fun `should expose OpenTelemetry bean so Micrometer bridges to the agent pipeline`() {
        assertThat(sentryOtelConfig.openTelemetry()).isInstanceOf(OpenTelemetry::class.java)
    }

    @Test
    fun `should provide a Micrometer Tracer so gen_ai observations become spans`() {
        val tracer = sentryOtelConfig.micrometerTracer(sentryOtelConfig.openTelemetry())
        assertThat(tracer).isInstanceOf(io.micrometer.tracing.Tracer::class.java)
    }

    @Test
    fun `should suppress HTTP observations the agent already instruments`() {
        val predicate: ObservationPredicate = sentryOtelConfig.suppressHttpObservationsHandledByAgent()
        assertThat(predicate.test("http.server.requests", Observation.Context())).isFalse()
        assertThat(predicate.test("http.client.requests", Observation.Context())).isFalse()
    }

    @Test
    fun `should suppress Spring Security per-request observation noise`() {
        val predicate: ObservationPredicate = sentryOtelConfig.suppressHttpObservationsHandledByAgent()
        assertThat(predicate.test("spring.security.filterchains", Observation.Context())).isFalse()
        assertThat(predicate.test("spring.security.authentications", Observation.Context())).isFalse()
        assertThat(predicate.test("spring.security.authorizations", Observation.Context())).isFalse()
    }

    @Test
    fun `should keep gen_ai and other observations flowing to Sentry`() {
        val predicate: ObservationPredicate = sentryOtelConfig.suppressHttpObservationsHandledByAgent()
        assertThat(predicate.test("gen_ai.client.operation", Observation.Context())).isTrue()
        assertThat(predicate.test("spring.ai.chat.client", Observation.Context())).isTrue()
    }
}