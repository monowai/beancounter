package com.beancounter.position.service

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * Dedicated executor for the fire-and-forget `ensureHistory` nudge in
 * [PerformanceService]. Keeps the calculate() request path off the network
 * round-trip — svc-data only schedules the backfill, but the HTTP call
 * itself can still add tens of ms and should not block the response.
 */
@Configuration
class PerformanceAsyncConfig {
    @Bean("performanceNudgeExecutor")
    fun performanceNudgeExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = CORE_POOL_SIZE
        executor.maxPoolSize = MAX_POOL_SIZE
        executor.queueCapacity = QUEUE_CAPACITY
        executor.setThreadNamePrefix("perf-nudge-")
        // Drop the oldest queued task if the queue saturates — the nudge is
        // best-effort and svc-data will eventually pick up the new range on
        // a later request anyway.
        executor.setRejectedExecutionHandler(
            java.util.concurrent.ThreadPoolExecutor
                .DiscardOldestPolicy()
        )
        executor.initialize()
        return executor
    }

    private companion object {
        const val CORE_POOL_SIZE = 1
        const val MAX_POOL_SIZE = 2
        const val QUEUE_CAPACITY = 32
    }
}