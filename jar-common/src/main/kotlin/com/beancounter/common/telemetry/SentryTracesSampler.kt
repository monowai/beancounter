package com.beancounter.common.telemetry

import io.sentry.SamplingContext
import io.sentry.SentryOptions
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Path-aware Sentry trace sampler.
 *
 * Registered by Sentry's Spring Boot auto-configuration because it implements
 * [SentryOptions.TracesSamplerCallback]. The SDK consults it **before** the
 * incoming `sentry-trace` parent decision and before `sentry.traces-sample-rate`,
 * so a non-null answer here is final; `null` defers to those defaults.
 *
 * Two jobs:
 *
 * 1. **Never sample noise** ([SentryNoisePaths]). Kubernetes probes alone are
 *    ~26k transactions a day per service — previously every one was sampled,
 *    fully recorded, then thrown away by [SentryTransactionFilter]. Deciding at
 *    sample time saves that work and the client-report churn.
 * 2. **Always sample the paths in `beancounter.sentry.always-sample-paths`**
 *    (comma-separated path prefixes, matched on segment boundaries), even when the caller's trace was *not* sampled.
 *    A trace started by bc-view carries its sampling decision downstream, and
 *    the Java SDK honours a parent's `sampled=false` regardless of this
 *    service's own rate — so at bc-view's rate almost no bc-agent chat trace
 *    ever landed and the per-request token telemetry was invisible
 *    (beancounter#1129). Low-volume, high-value routes opt in here; the
 *    downstream calls they make inherit `sampled=true` and complete the trace.
 *
 * The target is the OTel `url.path` attribute the sentry-opentelemetry-agent
 * supplies at span start (the route-templated transaction name is not known
 * yet), falling back to the legacy `http.target`, then `url.full` for outbound
 * root spans, then the transaction name.
 */
@Component
@ConditionalOnProperty(
    name = ["sentry.enabled"],
    havingValue = "true"
)
class SentryTracesSampler(
    @Value($$"${beancounter.sentry.always-sample-paths:}") alwaysSamplePaths: List<String>
) : SentryOptions.TracesSamplerCallback {
    private val alwaysSample = alwaysSamplePaths.map(String::trim).filter(String::isNotEmpty)

    override fun sample(samplingContext: SamplingContext): Double? {
        val target = target(samplingContext)
        return when {
            SentryNoisePaths.isNoise(target) -> NEVER
            alwaysSample.any { target == it || target.startsWith("$it/") } -> ALWAYS
            else -> null
        }
    }

    private fun target(samplingContext: SamplingContext): String =
        TARGET_ATTRIBUTES
            .firstNotNullOfOrNull { samplingContext.getAttribute(it)?.toString() }
            ?: samplingContext.transactionContext.name

    private companion object {
        const val NEVER = 0.0
        const val ALWAYS = 1.0
        val TARGET_ATTRIBUTES = listOf("url.path", "http.target", "url.full")
    }
}