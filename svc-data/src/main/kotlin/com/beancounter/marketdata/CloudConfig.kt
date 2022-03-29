package com.beancounter.marketdata

import org.slf4j.LoggerFactory
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration
import org.springframework.retry.annotation.EnableRetry
import javax.annotation.PostConstruct

/**
 * Spring Cloud Enablement
 * @author mikeh
 * @since 2019-03-03
 */
@Configuration
@EnableFeignClients
@EnableRetry
class CloudConfig {
    @PostConstruct
    fun loaded() {
        log.info("Enabled")
    }

    companion object {
        private val log = LoggerFactory.getLogger(CloudConfig::class.java)
    }
}
