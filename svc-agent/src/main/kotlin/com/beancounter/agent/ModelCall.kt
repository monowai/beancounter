package com.beancounter.agent

import io.opentelemetry.api.trace.Span

/**
 * What was fixed about a model call before it ran: the model selected, the
 * one reported to telemetry and the client (only where the per-call override
 * names what actually answers), the tools offered, the caller's question
 * (escaped, for logs), and the request's span — pinned at request time so
 * telemetry written on a Reactor thread lands on the request's http.server
 * span rather than a noop fallback.
 */
data class ModelCall(
    val modelId: String,
    val reportedModelId: String?,
    val tools: List<String>,
    val query: String,
    val span: Span = Span.current(),
    val startMs: Long = System.currentTimeMillis()
) {
    val elapsedMs: Long
        get() = System.currentTimeMillis() - startMs
}