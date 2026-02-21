package com.beancounter.position.cache

import com.beancounter.common.contracts.CacheChangeType
import com.beancounter.common.contracts.CacheInvalidationEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.function.Consumer

@Configuration
class CacheInvalidationConsumer(
    private val cacheService: PerformanceCacheService
) {
    @Bean
    fun performanceCacheInvalidation(): Consumer<CacheInvalidationEvent> =
        Consumer { event ->
            log.debug("Received cache invalidation event: {}", event)
            try {
                when (event.changeType) {
                    CacheChangeType.TRANSACTION -> {
                        val portfolioId = event.portfolioId
                        if (portfolioId != null) {
                            cacheService.invalidateFrom(portfolioId, event.fromDate)
                        }
                    }
                    CacheChangeType.PRICE, CacheChangeType.FX -> {
                        cacheService.invalidateOnDate(event.fromDate)
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to process cache invalidation event: {}", e.message)
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(CacheInvalidationConsumer::class.java)
    }
}