package com.beancounter.position.cache

import com.beancounter.common.contracts.CacheChangeType
import com.beancounter.common.contracts.CacheInvalidationEvent
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataAccessException
import java.util.function.Consumer

@Configuration
class CacheInvalidationConsumer(
    private val cacheService: PerformanceCacheService
) {
    /**
         * Creates a Consumer bean that handles incoming cache invalidation events and triggers the appropriate
         * invalidation methods on the `PerformanceCacheService`.
         *
         * For each `CacheInvalidationEvent`:
         * - `CacheChangeType.TRANSACTION`: if `portfolioId` is present, invalidates starting from `fromDate` for that portfolio.
         * - `CacheChangeType.PRICE` or `CacheChangeType.FX`: invalidates entries for the specific `fromDate`.
         * - `CacheChangeType.PRICE_HISTORY`: invalidates entries across all portfolios starting from `fromDate`.
         *
         * DataAccessException thrown during processing is caught and logged; it is not propagated.
         *
         * @return A `Consumer<CacheInvalidationEvent>` that applies the described invalidation behavior.
         */
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
                    // Deep price-history backfill — any portfolio holding the
                    // asset may need to recompute from fromDate onwards. We
                    // don't try to filter by portfolio here (svc-position
                    // doesn't have the asset→portfolio map); a broad sweep
                    // across all portfolios is correct and rare.
                    CacheChangeType.PRICE_HISTORY -> {
                        cacheService.invalidateFromDate(event.fromDate)
                    }
                }
            } catch (e: DataAccessException) {
                log.error("Failed to process cache invalidation event: {}", e.message)
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(CacheInvalidationConsumer::class.java)
    }
}