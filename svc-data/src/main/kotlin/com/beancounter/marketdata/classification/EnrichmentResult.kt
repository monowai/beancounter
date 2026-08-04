package com.beancounter.marketdata.classification

/**
 * Outcome of a single [ClassificationEnricher.enrichClassification] attempt.
 *
 * Prior to this type the enricher returned a bare [Boolean], so a provider rate-limit response
 * (HTTP 200 with an `Information`/`Note` throttle body) was indistinguishable from "nothing to
 * classify" — both returned `false` without ever incrementing an error count. That let a refresh
 * run silently no-op on the bulk of its candidates. Distinguishing the four outcomes lets
 * [ClassificationRefreshService] report honestly and decide whether an asset's
 * `classificationCheckedAt` should advance (it must not for [RATE_LIMITED] — the asset was never
 * actually checked, so it must be retried first on the next run).
 */
enum class EnrichmentResult {
    /** Provider returned usable data and it was persisted. */
    ENRICHED,

    /** Provider responded successfully but had nothing for this asset. */
    NO_DATA,

    /** An exception was thrown, or the provider returned an error response. */
    FAILED,

    /** Provider quota/rate limit was hit; the asset was not actually checked. */
    RATE_LIMITED
}