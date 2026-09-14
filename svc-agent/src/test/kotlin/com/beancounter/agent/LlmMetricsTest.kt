package com.beancounter.agent

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
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
        // A bare mock has no SpanContext; give it the invalid one a real
        // never-started span would carry, so parenting is skipped.
        whenever(span.spanContext).thenReturn(
            io.opentelemetry.api.trace.SpanContext
                .getInvalid()
        )

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
    fun `capture exports a span carrying the token counts`() {
        // The production failure this pins: over 7 days kauri recorded 45
        // `POST /agent/query/stream` transactions and not one span carrying
        // `agent.mode` or `llm.total_tokens` — every attribute write landed on
        // a span that was never exported, so token usage existed only in a
        // DEBUG log line. Writing to an ambient span is not verifiable; owning
        // the span is.
        val exporter = InMemorySpanExporter.create()
        val metrics = LlmMetrics(tracerFor(exporter))

        metrics.capture(
            modelId = "deepseek-v4-flash",
            usage = DefaultUsage(116608, 8601, 125209),
            elapsedMs = 47142,
            toolCount = 5,
            mode = LlmMetrics.Mode.STREAM
        )

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(1)
        val attributes =
            spans
                .first()
                .attributes
                .asMap()
                .mapKeys { it.key.key }
        // gen_ai.* is what Sentry's GenAI views read; llm.* is the name BC's
        // own queries already use. Both, so neither surface goes blind.
        assertThat(attributes["gen_ai.usage.input_tokens"]).isEqualTo(116608L)
        assertThat(attributes["gen_ai.usage.output_tokens"]).isEqualTo(8601L)
        assertThat(attributes["gen_ai.usage.total_tokens"]).isEqualTo(125209L)
        assertThat(attributes["llm.total_tokens"]).isEqualTo(125209L)
        assertThat(attributes["agent.model"]).isEqualTo("deepseek-v4-flash")
        assertThat(attributes["agent.mode"]).isEqualTo("stream")
        assertThat(attributes["agent.tools"]).isEqualTo(5L)
        assertThat(attributes["agent.elapsed_ms"]).isEqualTo(47142L)
    }

    @Test
    fun `capture exports a span even when the provider reported no usage`() {
        // An answerless turn is exactly when someone goes looking, so the call
        // has to leave a trace whether or not tokens came back with it.
        val exporter = InMemorySpanExporter.create()

        LlmMetrics(tracerFor(exporter)).capture(
            modelId = "deepseek-v4-flash",
            usage = null,
            elapsedMs = 900,
            toolCount = 5,
            mode = LlmMetrics.Mode.CALL
        )

        val spans = exporter.finishedSpanItems
        assertThat(spans).hasSize(1)
        val attributes =
            spans
                .first()
                .attributes
                .asMap()
                .mapKeys { it.key.key }
        assertThat(attributes["agent.mode"]).isEqualTo("call")
        assertThat(attributes).doesNotContainKey("llm.total_tokens")
    }

    @Test
    fun `the emitted span hangs off the request span it was given`() {
        // The controller pins `Span.current()` at request time precisely because
        // the done lambda runs on a Reactor thread with no ambient context. Use
        // it as the parent, or the telemetry lands as an orphan transaction
        // instead of under the request that produced it.
        val exporter = InMemorySpanExporter.create()
        val tracer = tracerFor(exporter)
        val requestSpan = tracer.spanBuilder("POST /agent/query/stream").startSpan()

        LlmMetrics(tracer).capture(
            modelId = "deepseek-v4-flash",
            usage = DefaultUsage(10, 5, 15),
            elapsedMs = 12,
            toolCount = 5,
            mode = LlmMetrics.Mode.STREAM,
            span = requestSpan
        )

        val emitted = exporter.finishedSpanItems.single { it.name == LlmMetrics.SPAN_NAME }
        assertThat(emitted.parentSpanId).isEqualTo(requestSpan.spanContext.spanId)
    }

    @Test
    fun `telemetry still lands when the request span has already ended`() {
        // The done lambda fires late; if the request span has closed by then,
        // parenting onto it risks the child being dropped — which is the same
        // silent loss this class was changed to end. An ended parent is skipped
        // and the span is emitted regardless: unparented telemetry beats none.
        val exporter = InMemorySpanExporter.create()
        val tracer = tracerFor(exporter)
        val endedRequestSpan = tracer.spanBuilder("POST /agent/query/stream").startSpan()
        endedRequestSpan.end()

        LlmMetrics(tracer).capture(
            modelId = "deepseek-v4-flash",
            usage = DefaultUsage(10, 5, 15),
            elapsedMs = 12,
            toolCount = 5,
            mode = LlmMetrics.Mode.STREAM,
            span = endedRequestSpan
        )

        val emitted = exporter.finishedSpanItems.single { it.name == LlmMetrics.SPAN_NAME }
        assertThat(emitted.attributes.asMap().mapKeys { it.key.key }["llm.total_tokens"]).isEqualTo(15L)
        assertThat(emitted.parentSpanId).isNotEqualTo(endedRequestSpan.spanContext.spanId)
    }

    private fun tracerFor(exporter: InMemorySpanExporter): io.opentelemetry.api.trace.Tracer =
        io.opentelemetry.sdk.trace.SdkTracerProvider
            .builder()
            .addSpanProcessor(
                io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
                    .create(exporter)
            ).build()
            .get("test")

    @Test
    fun `capture skips model attribute when modelId is null`() {
        val span = mock<Span>()
        whenever(span.setAttribute(any<String>(), any<String>())).thenReturn(span)
        whenever(span.setAttribute(any<String>(), any<Long>())).thenReturn(span)
        // A bare mock has no SpanContext; give it the invalid one a real
        // never-started span would carry, so parenting is skipped.
        whenever(span.spanContext).thenReturn(
            io.opentelemetry.api.trace.SpanContext
                .getInvalid()
        )

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