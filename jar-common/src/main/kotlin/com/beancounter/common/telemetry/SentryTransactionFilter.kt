package com.beancounter.common.telemetry

import io.sentry.Hint
import io.sentry.SentryOptions
import io.sentry.protocol.SentryTransaction
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

/**
 * Filters out noisy transactions from being sent to Sentry.
 *
 * This bean is automatically registered by Spring Boot's Sentry auto-configuration
 * when it implements SentryOptions.BeforeSendTransactionCallback.
 *
 * Filtered endpoints include:
 * - Actuator health/metrics endpoints
 * - Swagger/OpenAPI documentation
 * - Static resources (favicon, CSS, JS, images)
 */
@Component
@ConditionalOnProperty(
    name = ["sentry.enabled"],
    havingValue = "true"
)
class SentryTransactionFilter : SentryOptions.BeforeSendTransactionCallback {
    private val log = LoggerFactory.getLogger(SentryTransactionFilter::class.java)

    private val filterPatterns =
        listOf(
            // Actuator / management endpoints (any context path)
            Regex("/actuator"),
            Regex("/health"),
            Regex("/ready"),
            Regex("/live"),
            Regex("/ping"),
            Regex("/metrics"),
            Regex("/info"),
            Regex("/prometheus"),
            // Static resources
            Regex("/favicon\\.ico"),
            Regex("/webjars.*"),
            Regex("/css.*"),
            Regex("/js"),
            Regex("/images"),
            // API documentation
            Regex("/api-docs"),
            Regex("/swagger-ui.*"),
            Regex("/swagger-resources"),
            Regex("/openapi")
        )

    override fun execute(
        transaction: SentryTransaction,
        hint: Hint
    ): SentryTransaction? = filterTransaction(transaction)

    /**
     * Filters transaction based on HTTP target path or transaction name.
     * Returns null to drop the transaction, or the transaction to send it.
     */
    fun filterTransaction(transaction: SentryTransaction): SentryTransaction? {
        val httpTarget = extractHttpTarget(transaction)
        val transactionName = transaction.transaction ?: ""

        val targetToCheck = httpTarget ?: transactionName

        // Debug: log what we're checking
        log.trace(
            "Sentry filter check - target: {}, name: {}, shouldFilter: {}",
            httpTarget,
            transactionName,
            shouldFilter(targetToCheck)
        )

        return if (shouldFilter(targetToCheck)) {
            log.trace("Filtering transaction: {} (name: {})", httpTarget, transactionName)
            null
        } else {
            transaction
        }
    }

    private fun shouldFilter(httpTarget: String): Boolean = filterPatterns.any { it.containsMatchIn(httpTarget) }

    @Suppress("TooGenericExceptionCaught") // Defensive extraction - return null on any error
    private fun extractHttpTarget(transaction: SentryTransaction): String? {
        return try {
            // Try request URL first
            val requestUrl = transaction.request?.url
            if (!requestUrl.isNullOrBlank()) {
                return requestUrl
            }

            // Try OTel context attributes
            val otelContext = transaction.contexts["otel"] as? Map<*, *>
            val attributes = otelContext?.get("attributes") as? Map<*, *>
            (
                attributes?.get("http.target")
                    ?: attributes?.get("url.path")
                    ?: attributes?.get("url.full")
            )?.toString()
        } catch (e: Exception) {
            log.debug("Could not extract http.target from transaction: {}", e.message)
            null
        }
    }
}