package com.beancounter.marketdata.config

import org.slf4j.LoggerFactory
import org.springframework.cache.annotation.EnableCaching
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

@Configuration
@EnableCaching
class CacheConfig {
    @PostConstruct
    fun status() {
        log.info("Caching enabled")
    }

    companion object {
        private val log = LoggerFactory.getLogger(CacheConfig::class.java)
    }
}