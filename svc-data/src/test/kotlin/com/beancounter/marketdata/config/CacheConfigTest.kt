package com.beancounter.marketdata.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Verify cache configuration, specifically that alpha.asset.event
 * has a 10-minute TTL to ensure fresh dividend data from Alpha Vantage.
 */
class CacheConfigTest {
    private val cacheConfig = CacheConfig()

    @Test
    fun `alpha asset event cache should have 10 minute expiry`() {
        val cacheManager = cacheConfig.cacheManager()

        val cache = cacheManager.getCache("alpha.asset.event")
        assertThat(cache).isNotNull()
        assertThat(cache).isInstanceOf(CaffeineCache::class.java)

        val nativeCache =
            (cache as CaffeineCache).nativeCache
                as com.github.benmanes.caffeine.cache.Cache<*, *>
        val policy = nativeCache.policy()
        assertThat(policy.expireAfterWrite()).isPresent
        assertThat(
            policy
                .expireAfterWrite()
                .get()
                .expiresAfter
                .toMinutes()
        ).isEqualTo(10)
    }

    @Test
    fun `other caches should be available`() {
        val cacheManager = cacheConfig.cacheManager()

        assertThat(cacheManager.getCache("asset.prices")).isNotNull()
        assertThat(cacheManager.getCache("fx.rates")).isNotNull()
        assertThat(cacheManager.getCache("jwt.token")).isNotNull()
    }
}