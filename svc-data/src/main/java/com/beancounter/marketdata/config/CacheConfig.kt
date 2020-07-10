package com.beancounter.marketdata.config

import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

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