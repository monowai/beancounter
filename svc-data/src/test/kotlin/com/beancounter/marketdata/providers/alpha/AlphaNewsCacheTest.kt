package com.beancounter.marketdata.providers.alpha

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.cache.annotation.Cacheable

/**
 * Verify that AlphaNewsService is configured for caching.
 */
class AlphaNewsCacheTest {
    @Test
    fun `getNewsSentiment is annotated with Cacheable`() {
        val method =
            AlphaNewsService::class.java.getMethod(
                "getNewsSentiment",
                String::class.java,
                String::class.java,
                String::class.java
            )
        val cacheable = method.getAnnotation(Cacheable::class.java)
        assertThat(cacheable).isNotNull()
        assertThat(cacheable.value).contains("news.sentiment")
    }
}