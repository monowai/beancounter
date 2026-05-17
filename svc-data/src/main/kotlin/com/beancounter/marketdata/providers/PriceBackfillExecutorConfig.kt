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
    /**
     * Creates and configures a TaskExecutor dedicated to running price-history backfill work.
     *
     * The executor is a ThreadPoolTaskExecutor configured with a core pool size of 2, maximum pool size of 4,
     * a queue capacity of 64, and a thread name prefix of "price-backfill-".
     *
     * @return A configured ThreadPoolTaskExecutor for price backfill tasks.
     */
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