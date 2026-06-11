package com.beancounter.position.schedule

import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.TaskScheduler
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.atomic.AtomicReference

/**
 * Debounced revaluation trigger. Cache-invalidation events (PRICE / FX)
 * arrive from bc-data each time a price refresh completes; rather than
 * revalue every batch, we coalesce arrivals into a single run scheduled
 * `revaluation.trigger.debounce` after the most recent event (default
 * PT10M). A burst of refreshes therefore yields one revaluation ~10 min
 * after the burst settles.
 *
 * The actual loop is delegated to [PortfolioRevaluator], which also holds
 * a single-flight lock so the cron and the trigger can't double-run.
 */
@Service
@ConditionalOnProperty(
    value = ["revaluation.trigger.enabled"],
    havingValue = "true",
    matchIfMissing = false
)
class PortfolioRevaluationTrigger(
    private val revaluator: PortfolioRevaluator,
    @Value("\${revaluation.trigger.debounce:PT10M}") private val debounce: Duration,
    taskScheduler: TaskScheduler? = null
) {
    /**
     * Use the injected scheduler when one is on the classpath (kauri /
     * Spring Boot's auto-configured `ThreadPoolTaskScheduler`); otherwise
     * own a minimal one so the trigger works regardless of whether
     * `@EnableScheduling` is active in this profile.
     */
    private val scheduler: TaskScheduler = taskScheduler ?: defaultScheduler()
    private val ownsScheduler: Boolean = taskScheduler == null
    private val pending = AtomicReference<ScheduledFuture<*>?>()

    init {
        log.info("PortfolioRevaluationTrigger enabled with debounce {}", debounce)
    }

    @PreDestroy
    fun shutdown() {
        if (ownsScheduler && scheduler is ThreadPoolTaskScheduler) {
            scheduler.shutdown()
        }
    }

    /**
     * Schedule a revaluation `debounce` from now. Cancels any pending task
     * so later events extend the window — fits the "fire ~10 min after the
     * LAST price update of a batch" requirement.
     */
    @Synchronized
    fun scheduleRevaluation(reason: String) {
        val fireAt = Instant.now().plus(debounce)
        val task = scheduler.schedule({ revaluator.revalueAll(reason = reason) }, fireAt)
        val previous = pending.getAndSet(task)
        if (previous != null && previous.cancel(false)) {
            log.debug("Replaced pending revaluation; new fire at {} (reason={})", fireAt, reason)
        } else {
            log.info("Scheduled revaluation at {} (reason={})", fireAt, reason)
        }
    }

    /**
     * Test hook — exposed so the (rare) caller that wants to observe the
     * pending task can join on it. Production code should not depend on
     * this.
     */
    internal fun pendingTask(): ScheduledFuture<*>? = pending.get()

    private fun defaultScheduler(): ThreadPoolTaskScheduler =
        ThreadPoolTaskScheduler().apply {
            poolSize = 1
            setThreadNamePrefix("revaluation-trigger-")
            setRemoveOnCancelPolicy(true)
            initialize()
        }

    companion object {
        private val log = LoggerFactory.getLogger(PortfolioRevaluationTrigger::class.java)
    }
}