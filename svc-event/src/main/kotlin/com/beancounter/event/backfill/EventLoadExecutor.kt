package com.beancounter.event.backfill

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * The threads that run background event loads, kept apart from request and scheduler
 * threads. Deliberately not a java.util.concurrent.Executor bean: one of those would
 * make Spring Boot back off its applicationTaskExecutor.
 */
class EventLoadExecutor(
    private val executor: ExecutorService
) : AutoCloseable {
    fun execute(task: Runnable) = executor.execute(task)

    /** Interrupts running loads; they are not resumed on the next start. */
    override fun close() {
        executor.shutdownNow()
    }
}

@Configuration
class EventLoadExecutorConfig {
    @Bean(destroyMethod = "close")
    fun eventLoadExecutor(): EventLoadExecutor {
        val threadCount = AtomicInteger()
        return EventLoadExecutor(
            Executors.newFixedThreadPool(LOAD_THREADS) { task ->
                Thread(task, "event-load-${threadCount.incrementAndGet()}").apply { isDaemon = true }
            }
        )
    }

    private companion object {
        const val LOAD_THREADS = 2
    }
}