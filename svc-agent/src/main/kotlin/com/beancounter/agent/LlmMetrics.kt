package com.beancounter.agent

import io.sentry.Sentry
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.metadata.Usage
import org.springframework.stereotype.Component

/**
 * Publishes per-call LLM telemetry to Sentry so token usage is queryable
 * from the events / measurements UI without parsing kauri pod stdout.
 *
 * Strategy: piggyback on the inbound HTTP request's Sentry transaction
 * (Spring's Sentry integration auto-creates one per request). Each LLM
 * call adds:
 *   - tags    `agent.model`, `agent.tools`, `agent.mode` (call/stream)
 *   - data    `agent.elapsed_ms`
 *   - measurements `prompt_tokens`, `completion_tokens`, `total_tokens`
 *
 * Sentry's events search supports filtering on `measurements.total_tokens`
 * and aggregating sum/avg per `agent.model` tag.
 */
@Component
class LlmMetrics {
    private val log = LoggerFactory.getLogger(LlmMetrics::class.java)

    fun capture(
        modelId: String?,
        usage: Usage?,
        elapsedMs: Long,
        toolCount: Int,
        mode: Mode
    ) {
        @Suppress("TooGenericExceptionCaught")
        // Sentry's SDK can surface unexpected errors (uninitialised, IO, etc).
        // Telemetry must never break the user-visible response, so we catch
        // broadly and log at DEBUG. Narrowing the type would let an unfamiliar
        // failure mode crash the request thread purely to satisfy the linter.
        try {
            val tx = Sentry.getCurrentScopes().transaction
            if (tx == null) {
                // No active Sentry transaction (e.g. unit test, Sentry disabled
                // by profile). Bail silently — metrics are best-effort.
                return
            }
            // Only tag the model when we actually know it. On ollama / openai
            // profiles the per-call selected id doesn't reflect what answered
            // the request, so attributing measurements to it would mislead
            // dashboards. Skip the tag rather than poison the data.
            if (!modelId.isNullOrBlank()) tx.setTag(TAG_MODEL, modelId)
            tx.setTag(TAG_TOOLS, toolCount.toString())
            tx.setTag(TAG_MODE, mode.tag)
            tx.setData(DATA_ELAPSED_MS, elapsedMs)
            usage?.promptTokens?.toLong()?.let { tx.setMeasurement(M_PROMPT_TOKENS, it) }
            usage?.completionTokens?.toLong()?.let { tx.setMeasurement(M_COMPLETION_TOKENS, it) }
            usage?.totalTokens?.toLong()?.let { tx.setMeasurement(M_TOTAL_TOKENS, it) }
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
        private const val TAG_MODEL = "agent.model"
        private const val TAG_TOOLS = "agent.tools"
        private const val TAG_MODE = "agent.mode"
        private const val DATA_ELAPSED_MS = "agent.elapsed_ms"
        private const val M_PROMPT_TOKENS = "prompt_tokens"
        private const val M_COMPLETION_TOKENS = "completion_tokens"
        private const val M_TOTAL_TOKENS = "total_tokens"
    }
}