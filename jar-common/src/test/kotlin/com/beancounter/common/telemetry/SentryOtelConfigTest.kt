package com.beancounter.common.telemetry

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
}