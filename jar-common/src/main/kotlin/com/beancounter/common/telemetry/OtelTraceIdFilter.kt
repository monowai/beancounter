package com.beancounter.common.telemetry

import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.DependsOn
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

private const val TRACE_ID = "traceId"

private const val SPAN_ID = "spanId"

/**
 * Filter to add the traceId to the MDC logging context.
 */
@Component
@DependsOn("propertySourcesPlaceholderConfigurer")
@Order(Ordered.HIGHEST_PRECEDENCE)
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
            MDC.put(
                TRACE_ID,
                Span.fromContext(Context.current()).spanContext.traceId
            )
            MDC.put(
                SPAN_ID,
                Span.fromContext(Context.current()).spanContext.spanId
            )
            filterChain.doFilter(
                request,
                response
            )
        } finally {
            MDC.remove(TRACE_ID)
            MDC.remove(SPAN_ID)
        }
    }
}