package com.beancounter.marketdata.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.ThreadPoolExecutor

/**
 * Price executor configuration
 */
@Configuration
@EnableAsync
class PriceExecutorConfig {
    @Value("\${beancounter.price.thread.core-pool:2}")
    private val corePoolSize = 2

    @Value("\${beancounter.price.thread.max-pool:20}")
    private val maxPoolSize = 20

    @Value("\${beancounter.price.queue.capacity:50}")
    private val queueCapacity = 50

    @Value("\${beancounter.price.thread.timeout:10}")
    private val threadTimeout = 10

    @Bean
    fun priceExecutor(): ThreadPoolTaskExecutor? {
        val priceExecutor = ThreadPoolTaskExecutor()
        priceExecutor.setThreadGroupName("price-")
        priceExecutor.corePoolSize = corePoolSize
        priceExecutor.maxPoolSize = maxPoolSize
        priceExecutor.setRejectedExecutionHandler(ThreadPoolExecutor.CallerRunsPolicy())
        priceExecutor.queueCapacity = queueCapacity
        priceExecutor.keepAliveSeconds = threadTimeout
        return priceExecutor
    }
}
