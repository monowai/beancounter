package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable

/**
 * Verify that [TreasuryYieldFetcher.fetch] is configured for caching, keyed by maturity so the
 * `/macro/indicators` endpoint and [MacroRefreshSchedule]'s warm-up fetch share one entry per
 * maturity. Mirrors [com.beancounter.marketdata.providers.alpha.AlphaNewsCacheTest].
 */
class TreasuryYieldCacheTest {
    @Test
    fun `fetch is annotated with Cacheable`() {
        val method = TreasuryYieldFetcher::class.java.getMethod("fetch", String::class.java)
        val cacheable = method.getAnnotation(Cacheable::class.java)
        assertThat(cacheable).isNotNull()
        assertThat(cacheable.value).contains("alpha.treasury.yield")
        assertThat(cacheable.key).contains("maturity")
    }
}