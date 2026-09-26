package com.beancounter.common.telemetry

import io.sentry.SamplingContext
import io.sentry.TransactionContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

/**
 * Unit tests for [SentryTracesSampler] — the sample-time counterpart of
 * [SentryTransactionFilter].
 */
class SentryTracesSamplerTest {
    private val sampler = SentryTracesSampler(listOf("/agent/query"))

    @ParameterizedTest
    @ValueSource(
        strings = [
            "/actuator/health/livenessState",
            "/actuator/health/readinessState",
            "/actuator/prometheus",
            "/favicon.ico"
        ]
    )
    fun `should never sample probe and static noise`(path: String) {
        assertThat(sampler.sample(context(path))).isEqualTo(0.0)
    }

    @Test
    fun `should never sample noise even when the parent trace was sampled`() {
        val context = context("/actuator/health", parentSampled = true)

        assertThat(sampler.sample(context)).isEqualTo(0.0)
    }

    @ParameterizedTest
    @ValueSource(strings = ["/agent/query", "/agent/query/stream"])
    fun `should always sample configured paths when the parent trace was not sampled`(path: String) {
        val context = context(path, parentSampled = false)

        assertThat(sampler.sample(context)).isEqualTo(1.0)
    }

    @Test
    fun `should always sample configured paths with no parent trace`() {
        assertThat(sampler.sample(context("/agent/query/stream"))).isEqualTo(1.0)
    }

    @Test
    fun `should defer to the SDK for ordinary API paths`() {
        assertThat(sampler.sample(context("/api/portfolios", parentSampled = false))).isNull()
        assertThat(sampler.sample(context("/api/portfolios"))).isNull()
    }

    @Test
    fun `should not treat a path that merely contains a configured prefix as always-sample`() {
        assertThat(sampler.sample(context("/api/agent/query"))).isNull()
    }

    @Test
    fun `should fall back to the legacy http target attribute`() {
        val context =
            SamplingContext(
                TransactionContext("POST", "http.server"),
                null,
                0.5,
                mapOf("http.target" to "/agent/query/stream")
            )

        assertThat(sampler.sample(context)).isEqualTo(1.0)
    }

    @Test
    fun `should drop outbound JWKS root spans by their full url`() {
        val context =
            SamplingContext(
                TransactionContext("GET", "http.client"),
                null,
                0.5,
                mapOf("url.full" to "https://beancounter.eu.auth0.com/.well-known/jwks.json")
            )

        assertThat(sampler.sample(context)).isEqualTo(0.0)
    }

    @Test
    fun `should fall back to the transaction name when no url attribute exists`() {
        val noise = SamplingContext(TransactionContext("GET /actuator/health", "http.server"), null, 0.5, null)
        val api = SamplingContext(TransactionContext("GET /api/portfolios", "http.server"), null, 0.5, null)

        assertThat(sampler.sample(noise)).isEqualTo(0.0)
        assertThat(sampler.sample(api)).isNull()
    }

    @Test
    fun `should ignore blank always-sample entries`() {
        val blankConfig = SentryTracesSampler(listOf("", " "))

        assertThat(blankConfig.sample(context("/agent/query/stream", parentSampled = false))).isNull()
        assertThat(blankConfig.sample(context("/actuator/health"))).isEqualTo(0.0)
    }

    private fun context(
        path: String,
        parentSampled: Boolean? = null
    ): SamplingContext {
        val transactionContext =
            TransactionContext("POST", "http.server").apply {
                parentSampled?.let { setParentSampled(it) }
            }
        return SamplingContext(transactionContext, null, 0.5, mapOf("url.path" to path))
    }
}