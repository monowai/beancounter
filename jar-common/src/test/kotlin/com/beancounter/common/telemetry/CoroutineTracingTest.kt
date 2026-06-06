package com.beancounter.common.telemetry

import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Regression tests for the OTel-Context propagation bug that left
 * svc-data `/assets/search` invisible in Sentry: the controller wraps
 * its provider call in `runBlocking(Dispatchers.IO) { ... }`, which
 * drops the current OTel `Context` on resume, detaching the
 * `http.server` span.
 */
class CoroutineTracingTest {
    private val traceKey: ContextKey<String> = ContextKey.named("bc-test-trace")

    @Test
    fun `bare runBlocking on IO dispatcher loses OTel Context`() {
        val outer = Context.current().with(traceKey, OUTER_VALUE)
        val captured =
            outer.makeCurrent().use {
                runBlocking(Dispatchers.IO) {
                    Context.current().get(traceKey)
                }
            }
        // Documents the bug. If this ever starts returning the outer value
        // the upstream coroutines/OTel libs have started auto-propagating
        // and [runBlockingTraced] becomes a no-op.
        assertThat(captured).isNull()
    }

    @Test
    fun `runBlockingTraced propagates OTel Context across dispatchers`() {
        val outer = Context.current().with(traceKey, OUTER_VALUE)
        val captured =
            outer.makeCurrent().use {
                runBlockingTraced(Dispatchers.IO) {
                    Context.current().get(traceKey)
                }
            }
        assertThat(captured).isEqualTo(OUTER_VALUE)
    }

    private companion object {
        const val OUTER_VALUE = "outer-value"
    }
}