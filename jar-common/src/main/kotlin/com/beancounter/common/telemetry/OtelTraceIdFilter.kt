package com.beancounter.common.telemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.sentry.Sentry
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val TRACE_ID = "traceId"
private const val SPAN_ID = "spanId"

/**
 * Filter to add traceId/spanId to MDC logging context.
 * Prefers OTel context when available, falls back to Sentry.
 * Runs after Sentry's SentryTracingFilter (which has order HIGHEST_PRECEDENCE + 1).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
@ConditionalOnProperty(
    name = ["sentry.enabled"],
    havingValue = "true"
)
class OtelTraceIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        try {
            val (traceId, spanId, _) = getTraceContext()
            MDC.put(TRACE_ID, traceId)
            MDC.put(SPAN_ID, spanId)
            // if (traceId.isNotEmpty()) {
            //     log.trace("MDC set from {} - traceId: {}, spanId: {}", source, traceId, spanId)
            // }
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove(TRACE_ID)
            MDC.remove(SPAN_ID)
        }
    }

    private fun getTraceContext(): Triple<String, String, String> {
        // Try OTel context first (when agent is running)
        val otelSpan = Span.fromContext(Context.current())
        val otelContext = otelSpan.spanContext
        if (otelContext.isValid) {
            return Triple(otelContext.traceId, otelContext.spanId, "OTel")
        }
        return sentryTraceContext()
    }

    /** Sentry's trace context, the fallback when no OTel span is active. */
    private fun sentryTraceContext(): Triple<String, String, String> =
        Sentry.getSpan()?.let { span ->
            val trace = span.toSentryTrace()
            Triple(trace.traceId.toString(), trace.spanId.toString(), "Sentry")
        } ?: Triple("", "", "none")
}