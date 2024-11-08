package com.beancounter.common.telemetry

import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.sentry.protocol.Contexts
import io.sentry.protocol.SentryTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer
import org.springframework.test.context.TestPropertySource

/**
 * Test SentryConfig is wired when enabled.
 */
@Suppress("UnstableApiUsage")
@SpringBootTest(classes = [SentryOtelConfig::class, PropertySourcesPlaceholderConfigurer::class])
@TestPropertySource(
    properties = [
        "sentry.dsn=https://1234f81278756cfe5644f42516cb2d4e@o4508146873466880.ingest.nowhere.sentry.io/1238146878447696",
        "sentry.environment=test-env",
        "sentry.debug=true",
        "sentry.traces-sample-rate=0.5",
        "sentry.enabled=true",
    ],
)
class SentryOtelConfigTest {
    @Autowired
    private lateinit var sdkTracerProvider: SdkTracerProvider

    @Autowired
    private lateinit var sentryOtelConfig: SentryOtelConfig

    private val filter = listOf(Regex("/actuator"))

    @Test
    fun testSentryConfig() {
        assertThat(sdkTracerProvider).isNotNull

        assertNotNull(sentryOtelConfig.sentryTaskDecorator())
        assertNotNull(sentryOtelConfig.otelLinkEventProcessor())
        assertNotNull(sentryOtelConfig.sdkTraceProvider())
    }

    @Test
    fun `filterTransaction should return null for filtered transaction route`() {
        val transaction = sentryTransaction("/actuator/health")

        val result =
            sentryOtelConfig.filterTransaction(
                transaction,
                filter,
            )

        assertNull(result)
    }

    @Test
    fun `filterTransaction should return transaction for api transaction route`() {
        val transaction = sentryTransaction("/api/resource")

        val result = sentryOtelConfig.filterTransaction(transaction, filter)

        assertNotNull(result)
    }

    private fun sentryTransaction(route: String): SentryTransaction {
        val transaction = mock(SentryTransaction::class.java)
        val otelContext = mock(Contexts::class.java)
        `when`(transaction.contexts).thenReturn(otelContext)
        val otelAttributes = mapOf("attributes" to mapOf("http.target" to route))
        `when`(transaction.contexts["otel"]).thenReturn(otelAttributes)
        return transaction
    }
}
