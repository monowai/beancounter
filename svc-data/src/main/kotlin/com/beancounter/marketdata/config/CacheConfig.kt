package com.beancounter.marketdata.config

import com.github.benmanes.caffeine.cache.Caffeine
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.CacheManager
import org.springframework.cache.annotation.EnableCaching
import org.springframework.cache.concurrent.ConcurrentMapCache
import org.springframework.cache.support.SimpleCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * Cache Enablement configuration.
 *
 * Configures alpha.asset.event with a 10-minute TTL via Caffeine
 * to ensure fresh dividend data from Alpha Vantage.
 * All other caches use ConcurrentMapCache (no expiry).
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(
    value = ["cache.enabled"],
    matchIfMissing = true
)
class CacheConfig {
    @PostConstruct
    fun status() {
        log.info("Caching enabled")
    }

    @Bean
    fun cacheManager(): CacheManager {
        val caches =
            DEFAULT_CACHES.map { name ->
                ConcurrentMapCache(name)
            } + CaffeineCache("alpha.asset.event", Duration.ofMinutes(10), 200)

        return SimpleCacheManager().apply {
            setCaches(caches)
            afterPropertiesSet()
        }
    }

    companion object {
        private val log = LoggerFactory.getLogger(CacheConfig::class.java)

        private val DEFAULT_CACHES =
            listOf(
                "system.user",
                "asset.prices",
                "asset.search",
                "asset.mstack.search",
                "fx.rates",
                "provider",
                "providers",
                "asset.ext",
                "currency.code",
                "currency.all",
                "jwt.token",
                "auth.m2m",
                "market.holidays"
            )
    }
}

/**
 * Spring Cache backed by Caffeine with configurable TTL.
 */
class CaffeineCache(
    private val cacheName: String,
    expireAfterWrite: Duration,
    maximumSize: Long
) : org.springframework.cache.Cache {
    val nativeCache: com.github.benmanes.caffeine.cache.Cache<Any, Any> =
        Caffeine
            .newBuilder()
            .expireAfterWrite(expireAfterWrite)
            .maximumSize(maximumSize)
            .build()

    override fun getName(): String = cacheName

    override fun getNativeCache(): Any = nativeCache

    override fun get(key: Any): org.springframework.cache.Cache.ValueWrapper? {
        val value = nativeCache.getIfPresent(key) ?: return null
        return org.springframework.cache.support
            .SimpleValueWrapper(value)
    }

    override fun <T> get(
        key: Any,
        type: Class<T>?
    ): T? {
        @Suppress("UNCHECKED_CAST")
        return nativeCache.getIfPresent(key) as? T
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T> get(
        key: Any,
        valueLoader: java.util.concurrent.Callable<T>
    ): T? {
        val result = nativeCache.get(key) { valueLoader.call() as Any }
        return result as? T
    }

    override fun put(
        key: Any,
        value: Any?
    ) {
        if (value != null) {
            nativeCache.put(key, value)
        } else {
            nativeCache.invalidate(key)
        }
    }

    override fun evict(key: Any) {
        nativeCache.invalidate(key)
    }

    override fun clear() {
        nativeCache.invalidateAll()
    }
}