package com.beancounter.agent

import io.micrometer.tracing.Tracer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext

/**
 * Boot-safety guard for the Sentry tracing wiring. With `sentry.enabled=true`
 * the SentryOtelConfig beans (OpenTelemetry + Micrometer Tracer + observation
 * predicate) activate. This proves the context starts cleanly — i.e. supplying
 * the Micrometer [Tracer] doesn't leave Boot's observation auto-config needing a
 * bean it can't find (the kind of failure that would CrashLoop on kauri) — and
 * that the Tracer bean Spring AI's gen_ai tracing handler depends on is present.
 *
 * Does NOT call an LLM; `GlobalOpenTelemetry.get()` is a no-op without the agent,
 * so the bridged spans are inert locally — the export itself is verified on kauri.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
    properties = [
        "sentry.enabled=true",
        "sentry.dsn=https://test@test.ingest.de.sentry.io/1",
        "sentry.environment=test",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://beancounter.eu.auth0.com/",
        "auth.audience=https://holdsworth.app"
    ]
)
class SentryTracingWiringTest {
    @Autowired
    private lateinit var context: ApplicationContext

    @Test
    fun `context exposes a Micrometer Tracer so gen_ai observations are traced`() {
        assertThat(context.getBeanNamesForType(Tracer::class.java)).isNotEmpty()
    }
}