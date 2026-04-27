package com.beancounter.agent

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.metadata.DefaultUsage

/**
 * LlmMetrics piggybacks on the active Sentry transaction, so when no
 * transaction is bound (unit-test environment, Sentry disabled) it must
 * silently no-op rather than throw and disturb the user-visible response.
 */
class LlmMetricsTest {
    @Test
    fun `capture is a no-op when no Sentry transaction is bound`() {
        val metrics = LlmMetrics()

        // Should not throw despite Sentry not being initialised in this test JVM.
        metrics.capture(
            modelId = "claude-haiku-4-5",
            usage = DefaultUsage(100, 50, 150),
            elapsedMs = 1234,
            toolCount = 4,
            mode = LlmMetrics.Mode.STREAM
        )
    }

    @Test
    fun `capture accepts a null modelId and is a no-op`() {
        // Controller passes null on ollama/openai profiles where the per-call
        // model id would be misleading. The signature must accept null and
        // not blow up downstream.
        val metrics = LlmMetrics()
        metrics.capture(
            modelId = null,
            usage = DefaultUsage(10, 5, 15),
            elapsedMs = 0,
            toolCount = 1,
            mode = LlmMetrics.Mode.CALL
        )
    }

    @Test
    fun `capture swallows null usage`() {
        val metrics = LlmMetrics()
        metrics.capture(
            modelId = "claude-sonnet-4-6",
            usage = null,
            elapsedMs = 0,
            toolCount = 0,
            mode = LlmMetrics.Mode.CALL
        )
    }

    @Test
    fun `Mode tag value is stable wire contract`() {
        // The string emitted as the agent.mode Sentry tag is part of the
        // observability contract — pin it so dashboards keep working.
        assertThat(LlmMetrics.Mode.CALL.tag).isEqualTo("call")
        assertThat(LlmMetrics.Mode.STREAM.tag).isEqualTo("stream")
    }

    @Test
    fun `capture survives an exception thrown by Sentry`() {
        // Use a stub usage that throws when read — proves the try/catch
        // around Sentry interaction shields the caller.
        val brokenUsage = mock<org.springframework.ai.chat.metadata.Usage>()
        whenever(brokenUsage.promptTokens).thenThrow(RuntimeException("simulated"))
        val metrics = LlmMetrics()
        metrics.capture(
            modelId = "x",
            usage = brokenUsage,
            elapsedMs = 0,
            toolCount = 0,
            mode = LlmMetrics.Mode.CALL
        )
    }
}