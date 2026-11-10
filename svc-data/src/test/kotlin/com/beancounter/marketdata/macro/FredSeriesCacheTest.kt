package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable

/**
 * Verify that [FredSeriesFetcher.fetch] is configured for caching, keyed by FRED series id so
 * the `/macro/indicators` endpoint and [MacroRefreshSchedule]'s warm-up fetch share one entry per
 * series. Mirrors [com.beancounter.marketdata.news.alpha.AlphaNewsCacheTest].
 */
class FredSeriesCacheTest {
    @Test
    fun `fetch is annotated with Cacheable keyed by series id`() {
        val method = FredSeriesFetcher::class.java.getMethod("fetch", String::class.java)
        val cacheable = method.getAnnotation(Cacheable::class.java)
        assertThat(cacheable).isNotNull()
        assertThat(cacheable.value).contains("macro.fred.series")
        assertThat(cacheable.key).contains("seriesId")
    }
}