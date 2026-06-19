package com.beancounter.position

import org.springframework.cache.CacheManager
import org.springframework.cache.concurrent.ConcurrentMapCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * jar-auth's WebAuthFilterConfig is annotated @EnableCaching but does not declare
 * a CacheManager. Under Boot 4 the integration test contexts no longer get an
 * implicit simple manager, so supply one for tests. Auto-applied via the
 * component scan of the `com.beancounter.position` package declared on PositionBoot.
 */
@Configuration
class TestCacheConfig {
    @Bean
    fun cacheManager(): CacheManager = ConcurrentMapCacheManager()
}
