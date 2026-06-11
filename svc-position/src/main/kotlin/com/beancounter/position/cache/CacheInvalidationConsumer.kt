package com.beancounter.position.cache

import com.beancounter.common.contracts.CacheChangeType
import com.beancounter.common.contracts.CacheInvalidationEvent
import com.beancounter.position.schedule.PortfolioRevaluationTrigger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.dao.DataAccessException
import java.util.function.Consumer

@Configuration
class CacheInvalidationConsumer(
    private val cacheService: PerformanceCacheService
) {
    /**
     * Event-driven revaluation trigger, present only when
     * `revaluation.trigger.enabled=true`. Optional so unit tests and the
     * local dev profile don't have to wire it.
     */
    private var revaluationTrigger: PortfolioRevaluationTrigger? = null

    @Autowired(required = false)
    fun setRevaluationTrigger(trigger: PortfolioRevaluationTrigger?) {
        this.revaluationTrigger = trigger
    }

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
                        // Debounced trigger: collapses a burst of refreshes into one
                        // revaluation ~10 min after the last event in the burst.
                        revaluationTrigger?.scheduleRevaluation(
                            reason = "cache:${event.changeType}"
                        )
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
                log.error("Failed to process cache invalidation event", e)
            }
        }

    companion object {
        private val log = LoggerFactory.getLogger(CacheInvalidationConsumer::class.java)
    }
}