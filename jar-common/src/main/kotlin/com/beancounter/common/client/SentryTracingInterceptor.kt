package com.beancounter.common.client

import io.sentry.Sentry
import io.sentry.SpanStatus
import org.springframework.http.HttpRequest
import org.springframework.http.client.ClientHttpRequestExecution
import org.springframework.http.client.ClientHttpRequestInterceptor
import org.springframework.http.client.ClientHttpResponse

/**
 * RestClient interceptor that adds Sentry tracing to HTTP requests.
 * Creates child spans and propagates trace context via sentry-trace and baggage headers.
 */
class SentryTracingInterceptor : ClientHttpRequestInterceptor {
    override fun intercept(
        request: HttpRequest,
        body: ByteArray,
        execution: ClientHttpRequestExecution
    ): ClientHttpResponse {
        val parentSpan = Sentry.getSpan()
        val span =
            parentSpan?.startChild(
                "http.client",
                "${request.method} ${request.uri.host}${request.uri.path}"
            )

        // Propagate trace context to downstream service
        Sentry.getTraceparent()?.let { traceparent ->
            request.headers.add(traceparent.name, traceparent.value)
        }
        Sentry.getBaggage()?.let { baggage ->
            request.headers.add(baggage.name, baggage.value)
        }

        return try {
            val response = execution.execute(request, body)
            span?.status = SpanStatus.fromHttpStatusCode(response.statusCode.value())
            response
        } catch (e: Exception) {
            span?.throwable = e
            span?.status = SpanStatus.INTERNAL_ERROR
            throw e
        } finally {
            span?.finish()
        }
    }
}