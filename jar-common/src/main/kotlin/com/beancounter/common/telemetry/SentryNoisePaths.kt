package com.beancounter.common.telemetry

/**
 * Request targets that are pure telemetry noise and must never reach Sentry.
 *
 * Shared by [SentryTracesSampler] (drops them at sample time, before the SDK
 * records a transaction) and [SentryTransactionFilter] (drops any that slip
 * through at send time — e.g. roots the OTel sampler never saw).
 *
 * - Actuator / management endpoints: Kubernetes liveness + readiness probes
 *   alone hit every service ~18 times a minute.
 * - Swagger / OpenAPI documentation and static resources.
 * - Unlabeled zero-duration DB root spans from the sentry-opentelemetry-agent.
 * - Outbound Auth0 JWKS fetches: self-healing background library work that
 *   always reports as `internal_error`.
 */
object SentryNoisePaths {
    val patterns: List<Regex> =
        listOf(
            // Actuator / management endpoints (any context path)
            segment("actuator", "health", "ready", "live", "ping", "metrics", "info", "prometheus"),
            // Static resources
            Regex("/favicon\\.ico$"),
            segment("webjars", "css", "js", "images"),
            // API documentation
            segment("api-docs", "swagger-ui", "swagger-resources", "openapi"),
            // Unlabeled DB root spans (no parent, no diagnostic value)
            Regex("^<unlabeled transaction>$"),
            // Outbound Auth0 JWKS fetches (self-healing background library work)
            Regex("/\\.well-known/jwks\\.json")
        )

    fun isNoise(target: String): Boolean = patterns.any { it.containsMatchIn(target) }

    /**
     * Match `/name` only as a whole path segment — followed by `/`, an extension
     * `.`, or the end — so `/health` never swallows `/healthcheck`, `/info`
     * never swallows `/api/information`, and `/js` never swallows `/json`.
     */
    private fun segment(vararg names: String): Regex = Regex("/(${names.joinToString("|")})(/|\\.|$)")
}