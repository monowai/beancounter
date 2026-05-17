package com.beancounter.agent

import io.opentelemetry.api.trace.Span
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
class LlmMetrics {
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
        } catch (e: Exception) {
            log.debug("LlmMetrics.capture failed: {}", e.message)
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
    }
}