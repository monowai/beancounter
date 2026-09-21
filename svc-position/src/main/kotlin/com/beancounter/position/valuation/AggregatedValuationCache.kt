package com.beancounter.position.valuation

import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import org.slf4j.LoggerFactory
import java.time.Duration

/**
 * Identity of one [ValuationService.getAggregatedPositions] call for cache
 * purposes. Includes the caller's JWT subject so two different users
 * requesting the same portfolio ids never share a cached response -
 * cross-user leakage here would be a data breach, not a stale read.
 *
 * [contextPortfolioId] is `portfolios.first().id` - the synthesised
 * aggregate's context portfolio (owner/id, and currency when
 * `targetCurrencyCode` is unset) is derived from whichever portfolio is
 * first in the caller's list, so a different ordering of the SAME id set
 * must be treated as a different key rather than collapsed onto whichever
 * ordering happened to load first.
 */
data class ValuationCacheKey(
    val subject: String,
    val portfolioIds: List<String>,
    val contextPortfolioId: String,
    val valuationDate: String,
    val value: Boolean,
    val targetCurrencyCode: String?
) {
    companion object {
        fun of(
            subject: String,
            portfolios: Collection<Portfolio>,
            valuationDate: String,
            value: Boolean,
            targetCurrencyCode: String?
        ): ValuationCacheKey =
            ValuationCacheKey(
                subject = subject,
                portfolioIds = portfolios.map { it.id }.distinct().sorted(),
                contextPortfolioId = portfolios.first().id,
                valuationDate = valuationDate,
                value = value,
                targetCurrencyCode = targetCurrencyCode?.uppercase()
            )
    }
}

/**
 * Short-TTL, single-flight cache in front of aggregated valuations.
 *
 * Evidence: a Sentry trace of one bc-view /independence page load showed
 * svc-position running 10 full aggregated valuations (9x GET /api/allocation
 * + 1x GET /api/aggregated) for the SAME user/portfolios/asAt within 2.6s,
 * each re-running the trns + proposed-cash + classifications + prices
 * fan-out to bc-data.
 *
 * Caffeine's `Cache.get(key, mappingFunction)` is single-flight: concurrent
 * callers with an identical key block on the first thread's computation
 * rather than each recomputing independently, so N near-simultaneous
 * identical calls only pay for the underlying fan-out once.
 *
 * No Spring `@Cacheable`: it gives no single-flight guarantee (each
 * concurrent miss recomputes independently - a thundering herd, not a
 * collapse) and no clean per-call bypass knob, both required here.
 *
 * Bounded to [MAX_ENTRIES] so subject churn (many distinct callers within
 * one TTL window) can't grow this unbounded - eviction is Caffeine's
 * default size-based policy (approximated LRU), independent of TTL expiry.
 */
class AggregatedValuationCache(
    ttlSeconds: Long,
    ticker: Ticker = Ticker.systemTicker()
) {
    private val cache: Cache<ValuationCacheKey, PositionResponse>? =
        if (ttlSeconds > 0) {
            Caffeine
                .newBuilder()
                .ticker(ticker)
                .expireAfterWrite(Duration.ofSeconds(ttlSeconds))
                .maximumSize(MAX_ENTRIES)
                .build()
        } else {
            null
        }

    /**
     * Exposes the configured maximum size for tests; null when the cache is
     * bypassed (ttl=0).
     */
    internal fun maximumSize(): Long? =
        cache
            ?.policy()
            ?.eviction()
            ?.orElse(null)
            ?.maximum

    /**
     * Returns the cached response for [key], computing it via [loader] on a
     * miss. A null [key] (caller identity could not be established) or a
     * zero/negative TTL bypasses the cache entirely and always loads fresh.
     */
    fun get(
        key: ValuationCacheKey?,
        loader: () -> PositionResponse
    ): PositionResponse {
        val activeCache = cache
        if (key == null || activeCache == null) {
            log.debug("Valuation cache bypassed")
            return loader()
        }
        var loaded = false
        val result =
            activeCache.get(key) {
                loaded = true
                loader()
            }
        log.debug(
            "Valuation cache {} (portfolios={})",
            if (loaded) "miss" else "hit",
            key.portfolioIds.size
        )
        return result
    }

    companion object {
        private val log = LoggerFactory.getLogger(AggregatedValuationCache::class.java)
        private const val MAX_ENTRIES = 500L
    }
}