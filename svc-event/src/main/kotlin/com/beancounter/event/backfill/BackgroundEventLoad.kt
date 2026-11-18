package com.beancounter.event.backfill

import com.beancounter.common.model.Portfolio
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.RejectedExecutionException

/** Outcome of asking for a background load. */
enum class BackgroundLoadStatus { STARTED, ALREADY_RUNNING }

/**
 * Runs a portfolio's event load (each held asset's events fetched once, then those from a
 * date to today processed: see [RangeEventLoader]) on the
 * dedicated [EventLoadExecutor], so the caller is answered before the work is done.
 *
 * One load per portfolio at a time: a start while that portfolio's load is queued or
 * running is refused with [BackgroundLoadStatus.ALREADY_RUNNING]. The slot is held in
 * memory only, so a restart forgets running loads; the interrupted load is not resumed.
 */
@Service
class BackgroundEventLoad(
    private val rangeEventLoader: RangeEventLoader,
    private val executor: EventLoadExecutor
) {
    private val running: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun start(
        portfolio: Portfolio,
        asAt: String
    ): BackgroundLoadStatus {
        if (!running.add(portfolio.id)) {
            log.info("Background event load already running, portfolio: ${describe(portfolio)}")
            return BackgroundLoadStatus.ALREADY_RUNNING
        }
        submit(portfolio, asAt)
        return BackgroundLoadStatus.STARTED
    }

    fun isRunning(portfolioId: String): Boolean = running.contains(portfolioId)

    private fun submit(
        portfolio: Portfolio,
        asAt: String
    ) {
        try {
            executor.execute { run(portfolio, asAt) }
        } catch (e: RejectedExecutionException) {
            running.remove(portfolio.id)
            throw e
        }
    }

    private fun run(
        portfolio: Portfolio,
        asAt: String
    ) {
        log.info("Background event load started, portfolio: ${describe(portfolio)}, asAt: $asAt")
        runCatching { rangeEventLoader.load(portfolio, LocalDate.parse(asAt)) }
            .onSuccess { log.info("Background event load finished, portfolio: ${describe(portfolio)}, asAt: $asAt") }
            .onFailure { log.error("Background event load failed, portfolio: ${describe(portfolio)}, asAt: $asAt", it) }
        running.remove(portfolio.id)
        // The load sets an M2M context on this pooled thread; do not leave it for the next job.
        SecurityContextHolder.clearContext()
    }

    private fun describe(portfolio: Portfolio) = "${portfolio.code}/${portfolio.id}"

    companion object {
        private val log = LoggerFactory.getLogger(BackgroundEventLoad::class.java)
    }
}