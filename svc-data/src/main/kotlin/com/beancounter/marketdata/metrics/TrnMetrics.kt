package com.beancounter.marketdata.metrics

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * Metrics for transaction event processing.
 * Tracks transaction import flow including duplicate detection.
 */
@Component
class TrnMetrics(
    private val meterRegistry: MeterRegistry
) {
    // Transaction event reception
    private val trnEventsReceived: Counter =
        Counter
            .builder("beancounter.trn.events.received")
            .description("Total transaction events received from message broker")
            .register(meterRegistry)

    // Transaction types
    private val dividendTrnReceived: Counter =
        Counter
            .builder("beancounter.trn.received.by_type")
            .description("Dividend transactions received")
            .tag("type", "DIVI")
            .register(meterRegistry)

    private val splitTrnReceived: Counter =
        Counter
            .builder("beancounter.trn.received.by_type")
            .description("Split transactions received")
            .tag("type", "SPLIT")
            .register(meterRegistry)

    // Transaction processing outcomes
    private val trnWritten: Counter =
        Counter
            .builder("beancounter.trn.written")
            .description("Transactions successfully written to database")
            .register(meterRegistry)

    private val trnDuplicates: Counter =
        Counter
            .builder("beancounter.trn.duplicates")
            .description("Duplicate transactions detected and skipped")
            .register(meterRegistry)

    private val trnIgnored: Counter =
        Counter
            .builder("beancounter.trn.ignored")
            .description("Transactions ignored (portfolio verification failed, etc.)")
            .register(meterRegistry)

    private val trnErrors: Counter =
        Counter
            .builder("beancounter.trn.errors")
            .description("Transaction processing errors")
            .register(meterRegistry)

    // Duplicate detection metrics
    private val duplicatesByType: MutableMap<String, Counter> = mutableMapOf()

    // Corporate event detection (from price data)
    private val dividendsDetectedInPrices: Counter =
        Counter
            .builder("beancounter.price.dividends.detected")
            .description("Dividends detected in price data feeds")
            .register(meterRegistry)

    private val splitsDetectedInPrices: Counter =
        Counter
            .builder("beancounter.price.splits.detected")
            .description("Splits detected in price data feeds")
            .register(meterRegistry)

    private val corporateEventsPublished: Counter =
        Counter
            .builder("beancounter.price.corporate_events.published")
            .description("Corporate events published to svc-event")
            .register(meterRegistry)

    // Processing time
    private val trnImportTimer: Timer =
        Timer
            .builder("beancounter.trn.import.time")
            .description("Time taken to import a transaction event")
            .register(meterRegistry)

    /**
     * Record that a transaction event was received.
     * @param trnType Type of transaction (DIVI, SPLIT, etc.)
     */
    fun recordTrnEventReceived(trnType: String) {
        trnEventsReceived.increment()
        when (trnType) {
            "DIVI" -> dividendTrnReceived.increment()
            "SPLIT" -> splitTrnReceived.increment()
            else ->
                Counter
                    .builder("beancounter.trn.received.by_type")
                    .description("Transactions received by type")
                    .tag("type", trnType)
                    .register(meterRegistry)
                    .increment()
        }
    }

    /**
     * Record that a transaction was successfully written to the database.
     * @param trnType Type of transaction
     * @param count Number of transactions written
     */
    fun recordTrnWritten(
        trnType: String,
        count: Int = 1
    ) {
        trnWritten.increment(count.toDouble())
        Counter
            .builder("beancounter.trn.written.by_type")
            .description("Transactions written by type")
            .tag("type", trnType)
            .register(meterRegistry)
            .increment(count.toDouble())
    }

    /**
     * Record that a duplicate transaction was detected and skipped.
     * @param trnType Type of transaction
     * @param withinDays Number of days within which duplicate was found
     */
    fun recordDuplicateDetected(
        trnType: String,
        withinDays: Long? = null
    ) {
        trnDuplicates.increment()

        val key = "duplicate_$trnType"
        val counter =
            duplicatesByType.getOrPut(key) {
                Counter
                    .builder("beancounter.trn.duplicates.by_type")
                    .description("Duplicate transactions by type")
                    .tag("type", trnType)
                    .register(meterRegistry)
            }
        counter.increment()

        if (withinDays != null) {
            Counter
                .builder("beancounter.trn.duplicates.within_days")
                .description("Duplicate transactions by days difference")
                .tag("days", withinDays.toString())
                .register(meterRegistry)
                .increment()
        }
    }

    /**
     * Record that a transaction was ignored.
     * @param reason Reason for ignoring (e.g., "portfolio_verification_failed")
     */
    fun recordTrnIgnored(reason: String) {
        trnIgnored.increment()
        Counter
            .builder("beancounter.trn.ignored.by_reason")
            .description("Transactions ignored by reason")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record a transaction processing error.
     * @param errorType Type of error
     */
    fun recordTrnError(errorType: String) {
        trnErrors.increment()
        Counter
            .builder("beancounter.trn.errors.by_type")
            .description("Transaction errors by type")
            .tag("error_type", errorType)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record that a dividend was detected in price data.
     */
    fun recordDividendDetected() {
        dividendsDetectedInPrices.increment()
    }

    /**
     * Record that a split was detected in price data.
     */
    fun recordSplitDetected() {
        splitsDetectedInPrices.increment()
    }

    /**
     * Record that a corporate event was published to svc-event.
     * @param eventType Type of event (DIVI, SPLIT)
     */
    fun recordCorporateEventPublished(eventType: String) {
        corporateEventsPublished.increment()
        Counter
            .builder("beancounter.price.corporate_events.published.by_type")
            .description("Corporate events published by type")
            .tag("type", eventType)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Time a transaction import operation.
     */
    fun <T> timeTransactionImport(operation: () -> T): T = trnImportTimer.recordCallable(operation)!!

    /**
     * Record import time manually.
     * @param durationMillis Duration in milliseconds
     */
    fun recordImportTime(durationMillis: Long) {
        trnImportTimer.record(durationMillis, TimeUnit.MILLISECONDS)
    }
}