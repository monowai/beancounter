package com.beancounter.marketdata.providers

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.task.TaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor

/**
 * Dedicated executor for price-history backfills so a slow provider call
 * doesn't tie up shared Spring threads. Pool sizes are externally tunable
 * (e.g. `price.backfill.executor.core-pool-size=4` in `application.yml`)
 * so a chart-traffic spike can be absorbed without a redeploy.
 */
@Configuration
class PriceBackfillExecutorConfig(
    @Value("\${price.backfill.executor.core-pool-size:2}")
    private val corePoolSize: Int,
    @Value("\${price.backfill.executor.max-pool-size:4}")
    private val maxPoolSize: Int,
    @Value("\${price.backfill.executor.queue-capacity:64}")
    private val queueCapacity: Int
) {
    @Bean("priceBackfillExecutor")
    fun priceBackfillExecutor(): TaskExecutor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = corePoolSize
        executor.maxPoolSize = maxPoolSize
        executor.queueCapacity = queueCapacity
        executor.setThreadNamePrefix("price-backfill-")
        executor.initialize()
        return executor
    }
}