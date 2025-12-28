package com.beancounter.common.telemetry

import io.sentry.protocol.Contexts
import io.sentry.protocol.SentryTransaction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Unit tests for SentryTransactionFilter.
 */
class SentryTransactionFilterTest {
    private lateinit var filter: SentryTransactionFilter

    @BeforeEach
    fun setUp() {
        filter = SentryTransactionFilter()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/actuator/health",
            "/actuator/metrics",
            "/actuator/info",
            "/favicon.ico",
            "/webjars/swagger-ui/index.html",
            "/css/style.css",
            "/js",
            "/images",
            "/api-docs",
            "/swagger-ui.html",
            "/swagger-ui/index.html",
            "/swagger-resources"
        ]
    )
    fun `should filter out noisy endpoints`(path: String) {
        val transaction = createMockTransaction(path)

        val result = filter.filterTransaction(transaction)

        assertThat(result).isNull()
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/api/portfolios",
            "/api/positions/123",
            "/api/assets",
            "/api/transactions",
            "/api/events"
        ]
    )
    fun `should not filter API endpoints`(path: String) {
        val transaction = createMockTransaction(path)

        val result = filter.filterTransaction(transaction)

        assertThat(result).isNotNull
        assertThat(result).isSameAs(transaction)
    }

    @Test
    fun `should handle transaction without otel context gracefully`() {
        val transaction = mock(SentryTransaction::class.java)
        val contexts = mock(Contexts::class.java)
        `when`(transaction.contexts).thenReturn(contexts)
        `when`(contexts["otel"]).thenReturn(null)

        val result = filter.filterTransaction(transaction)

        assertThat(result).isNotNull
        assertThat(result).isSameAs(transaction)
    }

    @Test
    fun `should handle malformed otel context gracefully`() {
        val transaction = mock(SentryTransaction::class.java)
        val contexts = mock(Contexts::class.java)
        `when`(transaction.contexts).thenReturn(contexts)
        `when`(contexts["otel"]).thenReturn(mapOf("invalid" to "data"))

        val result = filter.filterTransaction(transaction)

        assertThat(result).isNotNull
        assertThat(result).isSameAs(transaction)
    }

    private fun createMockTransaction(path: String): SentryTransaction {
        val transaction = mock(SentryTransaction::class.java)
        val contexts = mock(Contexts::class.java)
        `when`(transaction.contexts).thenReturn(contexts)
        val otelAttributes = mapOf("attributes" to mapOf("http.target" to path))
        `when`(contexts["otel"]).thenReturn(otelAttributes)
        return transaction
    }
}