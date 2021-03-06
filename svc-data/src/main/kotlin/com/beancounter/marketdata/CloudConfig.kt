package com.beancounter.marketdata

import org.slf4j.LoggerFactory
import org.springframework.cloud.openfeign.EnableFeignClients
import org.springframework.context.annotation.Configuration
import javax.annotation.PostConstruct

/**
 * Spring Cloud.
 * @author mikeh
 * @since 2019-03-03
 */
@Configuration
@EnableFeignClients
class CloudConfig {
    @PostConstruct
    fun loaded() {
        log.info("Loaded")
    }

    companion object {
        private val log = LoggerFactory.getLogger(CloudConfig::class.java)
    }
}
