package com.beancounter.marketdata.providers

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * Dedicated executor for price-history backfills so a slow provider call
 * doesn't tie up shared Spring threads.
 */
@Configuration
class PriceBackfillExecutorConfig {
    @Bean("priceBackfillExecutor")
    fun priceBackfillExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = CORE_POOL_SIZE
        executor.maxPoolSize = MAX_POOL_SIZE
        executor.queueCapacity = QUEUE_CAPACITY
        executor.setThreadNamePrefix("price-backfill-")
        executor.initialize()
        return executor
    }

    private companion object {
        const val CORE_POOL_SIZE = 2
        const val MAX_POOL_SIZE = 4
        const val QUEUE_CAPACITY = 64
    }
}