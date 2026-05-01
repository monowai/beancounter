package com.beancounter.agent

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.ai.chat.metadata.DefaultUsage

/**
 * LlmMetrics writes OTel span attributes. Outside an OTel context the
 * default `Span.current()` returns an invalid (noop) span so attribute
 * sets are silently dropped — must never throw and disturb the
 * user-visible response.
 */
class LlmMetricsTest {
    @Test
    fun `capture is a no-op when no OTel span is active`() {
        val metrics = LlmMetrics()

        // Should not throw despite no OTel javaagent attached in this JVM.
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
        // The string emitted as the agent.mode span attribute is part of
        // the observability contract — pin it so dashboards keep working.
        assertThat(LlmMetrics.Mode.CALL.tag).isEqualTo("call")
        assertThat(LlmMetrics.Mode.STREAM.tag).isEqualTo("stream")
    }

    @Test
    fun `capture survives an exception thrown while reading usage`() {
        // Use a stub usage that throws when read — proves the try/catch
        // around the OTel interaction shields the caller.
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

    @Test
    fun `capture writes token attributes onto the supplied span`() {
        // The streaming controller pins the request span and passes it
        // explicitly so the doneEvent lambda — which fires on a Reactor
        // boundedElastic thread without an active OTel context — still
        // writes telemetry to the http.server transaction.
        val span = mock<Span>()
        // Span builder methods return `this` in production; mockito returns
        // the bare mock by default for chained calls, which is fine here
        // because we only assert the verify(...) interactions.
        whenever(span.setAttribute(any<AttributeKey<String>>(), any<String>())).thenReturn(span)
        whenever(span.setAttribute(any<String>(), any<String>())).thenReturn(span)
        whenever(span.setAttribute(any<String>(), any<Long>())).thenReturn(span)

        val metrics = LlmMetrics()
        metrics.capture(
            modelId = "claude-haiku-4-5",
            usage = DefaultUsage(100, 50, 150),
            elapsedMs = 1234,
            toolCount = 4,
            mode = LlmMetrics.Mode.STREAM,
            span = span
        )

        verify(span).setAttribute(eq("agent.model"), eq("claude-haiku-4-5"))
        verify(span).setAttribute(eq("agent.tools"), eq(4L))
        verify(span).setAttribute(eq("agent.mode"), eq("stream"))
        verify(span).setAttribute(eq("agent.elapsed_ms"), eq(1234L))
        verify(span).setAttribute(eq("llm.prompt_tokens"), eq(100L))
        verify(span).setAttribute(eq("llm.completion_tokens"), eq(50L))
        verify(span).setAttribute(eq("llm.total_tokens"), eq(150L))
    }

    @Test
    fun `capture skips model attribute when modelId is null`() {
        val span = mock<Span>()
        whenever(span.setAttribute(any<String>(), any<String>())).thenReturn(span)
        whenever(span.setAttribute(any<String>(), any<Long>())).thenReturn(span)

        LlmMetrics().capture(
            modelId = null,
            usage = DefaultUsage(10, 5, 15),
            elapsedMs = 0,
            toolCount = 0,
            mode = LlmMetrics.Mode.CALL,
            span = span
        )

        verify(span, never()).setAttribute(eq("agent.model"), any<String>())
        verify(span).setAttribute(eq("agent.mode"), eq("call"))
        verify(span).setAttribute(eq("llm.total_tokens"), eq(15L))
    }
}