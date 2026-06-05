package com.beancounter.common.telemetry

import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

/**
 * Coroutine builders that propagate the current OpenTelemetry `Context`
 * (and therefore the active Sentry span / trace) into the launched
 * coroutine.
 *
 * Default `runBlocking { ... }` creates a fresh `CoroutineContext` with
 * no link to OTel's thread-local. When the inner coroutine resumes on
 * another dispatcher (e.g. `Dispatchers.IO`) `Context.current()` falls
 * back to root and any `http.server` span the OTel agent is recording
 * detaches — observed on `svc-data` `/assets/search`, the only
 * controller wrapping its work in `runBlocking`, whose `http.server`
 * spans never reach Sentry.
 *
 * Use [runBlockingTraced] in place of `runBlocking` for any blocking
 * coroutine started from a Spring MVC handler, scheduled job, or
 * message listener whose trace must continue.
 */
fun <T> runBlockingTraced(
    context: CoroutineContext = EmptyCoroutineContext,
    block: suspend CoroutineScope.() -> T
): T = runBlocking(Context.current().asContextElement() + context, block)