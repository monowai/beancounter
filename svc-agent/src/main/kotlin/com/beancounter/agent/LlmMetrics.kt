package com.beancounter.agent

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import io.opentelemetry.context.Context
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.metadata.Usage
import org.springframework.stereotype.Component

/**
 * Publishes per-call LLM telemetry as OpenTelemetry span attributes.
 *
 * The previous implementation wrote Sentry transaction measurements via
 * `Sentry.getCurrentScopes().transaction.setMeasurement`. That works on
 * the request thread for the non-streaming `/agent/query` path, but
 * silently no-ops on the SSE `/agent/query/stream` path because the
 * `doneEvent` lambda runs on a Reactor `boundedElastic` thread where the
 * Sentry scope is empty. Result: zero token data in Sentry's spans
 * dataset for any streamed call (which is the dominant traffic shape).
 *
 * Switching to OTel attributes solves both axes:
 *   - bc-agent already runs the `sentry-opentelemetry-agent` javaagent
 *     plus the `opentelemetry-instrumentation-reactor` autoload, which
 *     propagates the ambient `Span` across Reactor schedulers.
 *   - Sentry's OTel exporter reads OTel span attributes and surfaces them
 *     under the same span the http.server transaction already owns.
 *
 * Callers capture the request-time `Span.current()` and pass it to
 * [capture] so the lambda always writes to the right span — even if
 * Reactor's automatic context propagation regresses in a future agent
 * upgrade. The default-arg `Span.current()` keeps unit tests (where no
 * agent is attached) noop-safe — the SDK returns an invalid span and
 * `setAttribute` is a cheap no-op.
 */
@Component
class LlmMetrics(
    /**
     * Tracer used to emit the [SPAN_NAME] span. Resolved from the agent's
     * global OpenTelemetry — the same instance `SentryOtelConfig` bridges
     * Micrometer onto, which is how Spring AI's `gen_ai.*` spans already reach
     * Sentry. Injectable so tests can export to an in-memory exporter and
     * assert the attributes actually made it onto a finished span.
     */
    private val tracer: Tracer = GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION_SCOPE)
) {
    private val log = LoggerFactory.getLogger(LlmMetrics::class.java)

    fun capture(
        modelId: String?,
        usage: Usage?,
        elapsedMs: Long,
        toolCount: Int,
        mode: Mode,
        span: Span = Span.current()
    ) {
        @Suppress("TooGenericExceptionCaught")
        try {
            if (!modelId.isNullOrBlank()) span.setAttribute(ATTR_MODEL, modelId)
            span.setAttribute(ATTR_TOOLS, toolCount.toLong())
            span.setAttribute(ATTR_MODE, mode.tag)
            span.setAttribute(ATTR_ELAPSED_MS, elapsedMs)
            usage?.promptTokens?.toLong()?.let {
                span.setAttribute(ATTR_PROMPT_TOKENS, it)
            }
            usage?.completionTokens?.toLong()?.let {
                span.setAttribute(ATTR_COMPLETION_TOKENS, it)
            }
            usage?.totalTokens?.toLong()?.let {
                span.setAttribute(ATTR_TOTAL_TOKENS, it)
            }
            // Last: the span this class owns. Ordered after the ambient writes
            // so a tracer problem can only cost us the new surface, never the
            // decoration that was already there.
            emitSpan(modelId, usage, elapsedMs, toolCount, mode, span)
        } catch (e: Exception) {
            log.debug("LlmMetrics.capture failed: {}", e.message)
        }
    }

    /**
     * Emit a span this class owns, rather than only decorating whichever span
     * happens to be current.
     *
     * Decorating was the whole implementation, and in production it recorded
     * nothing: over seven days kauri served 45 `POST /agent/query/stream`
     * transactions and not one span carried `agent.mode` or `llm.total_tokens`.
     * Whether the ambient span had already ended, or was never the exported
     * one, the failure mode is identical and silent — `Span.setAttribute` on an
     * ended or invalid span is a no-op by design. A span we start and end
     * ourselves has no such ambiguity, and it still parents under the request's
     * transaction whenever there is one.
     *
     * Tokens are published under both `gen_ai.usage.*` (what Sentry's GenAI
     * views and the OTel semantic conventions read) and the existing `llm.*`
     * names, so adding one surface doesn't blind the other.
     */
    private fun emitSpan(
        modelId: String?,
        usage: Usage?,
        elapsedMs: Long,
        toolCount: Int,
        mode: Mode,
        parent: Span
    ) {
        val builder = tracer.spanBuilder(SPAN_NAME)
        // Parent explicitly off the pinned request span: on the streaming path
        // this runs on a Reactor thread whose ambient context is empty, and an
        // unparented span reaches Sentry as its own orphan transaction rather
        // than as part of the request that produced it.
        //
        // `isRecording` and not `spanContext.isValid`: an ended span keeps a
        // valid SpanContext forever, so validity alone would happily attach a
        // child to a closed parent — which exporters may drop, recreating the
        // silent loss this class exists to end. A late call simply goes
        // unparented instead; telemetry with no parent still beats no telemetry.
        if (parent.isRecording) builder.setParent(Context.current().with(parent))
        val span = builder.startSpan()
        try {
            if (!modelId.isNullOrBlank()) {
                span.setAttribute(ATTR_MODEL, modelId)
                span.setAttribute(ATTR_GENAI_MODEL, modelId)
            }
            span.setAttribute(ATTR_TOOLS, toolCount.toLong())
            span.setAttribute(ATTR_MODE, mode.tag)
            span.setAttribute(ATTR_ELAPSED_MS, elapsedMs)
            usage?.promptTokens?.toLong()?.let {
                span.setAttribute(ATTR_PROMPT_TOKENS, it)
                span.setAttribute(ATTR_GENAI_INPUT_TOKENS, it)
            }
            usage?.completionTokens?.toLong()?.let {
                span.setAttribute(ATTR_COMPLETION_TOKENS, it)
                span.setAttribute(ATTR_GENAI_OUTPUT_TOKENS, it)
            }
            usage?.totalTokens?.toLong()?.let {
                span.setAttribute(ATTR_TOTAL_TOKENS, it)
                span.setAttribute(ATTR_GENAI_TOTAL_TOKENS, it)
            }
        } finally {
            span.end()
        }
    }

    enum class Mode(
        val tag: String
    ) {
        CALL("call"),
        STREAM("stream")
    }

    companion object {
        private const val ATTR_MODEL = "agent.model"
        private const val ATTR_TOOLS = "agent.tools"
        private const val ATTR_MODE = "agent.mode"
        private const val ATTR_ELAPSED_MS = "agent.elapsed_ms"

        // Tokens use the `llm.*` namespace so Sentry's GenAI dashboards
        // and the standard `has:llm.total_tokens` event filter pick them
        // up without further config.
        private const val ATTR_PROMPT_TOKENS = "llm.prompt_tokens"
        private const val ATTR_COMPLETION_TOKENS = "llm.completion_tokens"
        private const val ATTR_TOTAL_TOKENS = "llm.total_tokens"

        // OTel GenAI semantic-convention names. Sentry's AI views key off
        // these, and they are what any other OTel-aware backend would expect.
        private const val ATTR_GENAI_MODEL = "gen_ai.request.model"
        private const val ATTR_GENAI_INPUT_TOKENS = "gen_ai.usage.input_tokens"
        private const val ATTR_GENAI_OUTPUT_TOKENS = "gen_ai.usage.output_tokens"
        private const val ATTR_GENAI_TOTAL_TOKENS = "gen_ai.usage.total_tokens"

        /** Instrumentation scope for the tracer this class emits under. */
        const val INSTRUMENTATION_SCOPE = "bc-agent-llm"

        /** Name of the span carrying per-call LLM telemetry. */
        const val SPAN_NAME = "agent.llm"
    }
}