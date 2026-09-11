package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable

/**
 * Verify that [RateExpectationsService.getRateExpectations] is configured for caching — avoids
 * one Kalshi `/markets` call per open event on every request within the TTL window. Mirrors
 * [TreasuryYieldCacheTest] / [com.beancounter.marketdata.news.alpha.AlphaNewsCacheTest].
 *
 * [RateExpectationsService.snapshot] must NOT be reached through this cache: it calls
 * [RateExpectationsService]'s private `nearestOpenEvent` fetch path directly, never
 * `getRateExpectations`, so a cached response is never mistaken for freshly-sampled odds.
 */
class RateExpectationsCacheTest {
    @Test
    fun `getRateExpectations is annotated with Cacheable`() {
        val method = RateExpectationsService::class.java.getMethod("getRateExpectations")
        val cacheable = method.getAnnotation(Cacheable::class.java)
        assertThat(cacheable).isNotNull()
        assertThat(cacheable.value).contains(RateExpectationsService.CACHE_NAME)
    }

    @Test
    fun `snapshot and prune are not annotated with Cacheable`() {
        val snapshot = RateExpectationsService::class.java.getMethod("snapshot")
        val prune = RateExpectationsService::class.java.getMethod("prune", Long::class.java)
        assertThat(snapshot.getAnnotation(Cacheable::class.java)).isNull()
        assertThat(prune.getAnnotation(Cacheable::class.java)).isNull()
    }
}