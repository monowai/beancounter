package com.beancounter.marketdata.config

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration

/**
 * Cache Enablement configuration.
 */
@Configuration
@EnableCaching
@ConditionalOnProperty(value = ["cache.enabled"], matchIfMissing = true)
class CacheConfig {
    @PostConstruct
    fun status() {
        log.info("Caching enabled")
    }

    companion object {
        private val log = LoggerFactory.getLogger(CacheConfig::class.java)
    }
}
