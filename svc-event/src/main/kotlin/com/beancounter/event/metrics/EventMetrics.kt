package com.beancounter.event.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Metrics for corporate event processing.
 * Tracks event processing flow from event reception through transaction creation.
 */
@Component
class EventMetrics(
    private val meterRegistry: MeterRegistry
) {
    // Event reception counters
    private val eventsReceived: Counter =
        Counter
            .builder("beancounter.event.received")
            .description("Total corporate events received from message broker")
            .tag("type", "all")
            .register(meterRegistry)

    private val dividendEventsReceived: Counter =
        Counter
            .builder("beancounter.event.received")
            .description("Dividend events received")
            .tag("type", "dividend")
            .register(meterRegistry)

    private val splitEventsReceived: Counter =
        Counter
            .builder("beancounter.event.received")
            .description("Split events received")
            .tag("type", "split")
            .register(meterRegistry)

    // Event processing outcomes
    private val eventsProcessed: Counter =
        Counter
            .builder("beancounter.event.processed")
            .description("Events successfully processed (resulted in transactions)")
            .register(meterRegistry)

    private val eventsIgnored: Counter =
        Counter
            .builder("beancounter.event.ignored")
            .description("Events ignored (no positions, future dated, etc.)")
            .register(meterRegistry)

    private val eventsErrored: Counter =
        Counter
            .builder("beancounter.event.errors")
            .description("Events that failed to process")
            .register(meterRegistry)

    // Portfolio processing
    private val portfoliosAnalyzed: Counter =
        Counter
            .builder("beancounter.event.portfolios.analyzed")
            .description("Number of portfolios analyzed for corporate events")
            .register(meterRegistry)

    private val transactionsCreated: Counter =
        Counter
            .builder("beancounter.event.transactions.created")
            .description("Transactions created from corporate events")
            .register(meterRegistry)

    // Backfill operations
    private val backfillOperations: Counter =
        Counter
            .builder("beancounter.event.backfill.operations")
            .description("Backfill operations executed")
            .register(meterRegistry)

    private val backfillEventsLoaded: Counter =
        Counter
            .builder("beancounter.event.backfill.events.loaded")
            .description("Events loaded during backfill operations")
            .register(meterRegistry)

    // Processing latency
    private val eventProcessingTimer: Timer =
        Timer
            .builder("beancounter.event.processing.time")
            .description("Time taken to process a corporate event")
            .register(meterRegistry)

    /**
     * Record that an event was received from the message broker.
     */
    fun recordEventReceived(isDividend: Boolean = true) {
        eventsReceived.increment()
        if (isDividend) {
            dividendEventsReceived.increment()
        } else {
            splitEventsReceived.increment()
        }
    }

    /**
     * Record successful event processing.
     * @param portfolioCount Number of portfolios that were analyzed
     * @param transactionCount Number of transactions created
     */
    fun recordEventProcessed(
        portfolioCount: Int,
        transactionCount: Int
    ) {
        eventsProcessed.increment()
        portfoliosAnalyzed.increment(portfolioCount.toDouble())
        transactionsCreated.increment(transactionCount.toDouble())
    }

    /**
     * Record that an event was ignored (no action taken).
     * @param reason Why the event was ignored (e.g., "no_positions", "future_dated")
     */
    fun recordEventIgnored(reason: String) {
        eventsIgnored.increment()
        Counter
            .builder("beancounter.event.ignored.by_reason")
            .description("Events ignored by reason")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record an error during event processing.
     * @param errorType Type of error (e.g., "position_query_failed", "transaction_creation_failed")
     */
    fun recordEventError(errorType: String) {
        eventsErrored.increment()
        Counter
            .builder("beancounter.event.errors.by_type")
            .description("Event processing errors by type")
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record a backfill operation.
     * @param eventsLoaded Number of events loaded from external sources
     */
    fun recordBackfillOperation(eventsLoaded: Int) {
        backfillOperations.increment()
        backfillEventsLoaded.increment(eventsLoaded.toDouble())
    }

    /**
     * Time an event processing operation.
     */
    fun <T> timeEventProcessing(operation: () -> T): T = eventProcessingTimer.recordCallable(operation)!!

    /**
     * Record processing time manually.
     * @param durationMillis Duration in milliseconds
     */
    fun recordProcessingTime(durationMillis: Long) {
        eventProcessingTimer.record(durationMillis, TimeUnit.MILLISECONDS)
    }
}