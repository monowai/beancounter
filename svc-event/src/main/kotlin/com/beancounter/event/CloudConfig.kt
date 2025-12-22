package com.beancounter.event

import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration

/**
 * Spring Cloud.
 *
 * @author mikeh
 * @since 2019-03-03
 */
@Configuration
class CloudConfig {
    @PostConstruct
    fun loaded() {
        log.info("Loaded")
    }

    companion object {
        private val log = LoggerFactory.getLogger(CloudConfig::class.java)
    }
}