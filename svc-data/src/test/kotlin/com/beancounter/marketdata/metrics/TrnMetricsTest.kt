package com.beancounter.marketdata.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TrnMetricsTest {
    companion object {
        const val METRIC_TRN_EVENTS_RECEIVED = "beancounter.trn.events.received"
        const val METRIC_TRN_RECEIVED_BY_TYPE = "beancounter.trn.received.by_type"
        const val METRIC_TRN_WRITTEN = "beancounter.trn.written"
        const val METRIC_TRN_WRITTEN_BY_TYPE = "beancounter.trn.written.by_type"
        const val METRIC_TRN_DUPLICATES = "beancounter.trn.duplicates"
        const val METRIC_TRN_DUPLICATES_BY_TYPE = "beancounter.trn.duplicates.by_type"
        const val METRIC_TRN_DUPLICATES_WITHIN_DAYS = "beancounter.trn.duplicates.within_days"
        const val METRIC_TRN_IGNORED = "beancounter.trn.ignored"
        const val METRIC_TRN_IGNORED_BY_REASON = "beancounter.trn.ignored.by_reason"
        const val METRIC_TRN_IMPORT_TIME = "beancounter.trn.import.time"
        const val METRIC_EVENTS_PUBLISHED = "beancounter.price.corporate_events.published"
        const val METRIC_EVENTS_PUBLISHED_BY_TYPE = "beancounter.price.corporate_events.published.by_type"
        const val TAG_TYPE = "type"
        const val TAG_DAYS = "days"
    }

    private lateinit var meterRegistry: MeterRegistry
    private lateinit var trnMetrics: TrnMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        trnMetrics = TrnMetrics(meterRegistry)
    }

    @Test
    fun `should record DIVI transaction received`() {
        // When
        trnMetrics.recordTrnEventReceived("DIVI")

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_EVENTS_RECEIVED).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_RECEIVED_BY_TYPE, TAG_TYPE, "DIVI").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record SPLIT transaction received`() {
        // When
        trnMetrics.recordTrnEventReceived("SPLIT")

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_EVENTS_RECEIVED).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_RECEIVED_BY_TYPE, TAG_TYPE, "SPLIT").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record other transaction types`() {
        // When
        trnMetrics.recordTrnEventReceived("BUY")

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_EVENTS_RECEIVED).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_RECEIVED_BY_TYPE, TAG_TYPE, "BUY").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record transaction written with count`() {
        // When
        trnMetrics.recordTrnWritten("DIVI", 3)

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_WRITTEN).count())
            .isEqualTo(3.0)
        assertThat(meterRegistry.counter(METRIC_TRN_WRITTEN_BY_TYPE, TAG_TYPE, "DIVI").count())
            .isEqualTo(3.0)
    }

    @Test
    fun `should record single transaction written by default`() {
        // When
        trnMetrics.recordTrnWritten("DIVI")

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_WRITTEN).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_WRITTEN_BY_TYPE, TAG_TYPE, "DIVI").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should accumulate transactions written`() {
        // When
        trnMetrics.recordTrnWritten("DIVI", 2)
        trnMetrics.recordTrnWritten("DIVI", 3)

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_WRITTEN).count())
            .isEqualTo(5.0)
        assertThat(meterRegistry.counter(METRIC_TRN_WRITTEN_BY_TYPE, TAG_TYPE, "DIVI").count())
            .isEqualTo(5.0)
    }

    @Test
    fun `should record duplicate detected with days difference`() {
        // When
        trnMetrics.recordDuplicateDetected("DIVI", withinDays = 5)

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES_BY_TYPE, TAG_TYPE, "DIVI").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES_WITHIN_DAYS, TAG_DAYS, "5").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record duplicate detected without days difference`() {
        // When
        trnMetrics.recordDuplicateDetected("DIVI", withinDays = null)

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES_BY_TYPE, TAG_TYPE, "DIVI").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should track duplicates by different day ranges`() {
        // When
        trnMetrics.recordDuplicateDetected("DIVI", withinDays = 0)
        trnMetrics.recordDuplicateDetected("DIVI", withinDays = 5)
        trnMetrics.recordDuplicateDetected("DIVI", withinDays = 20)
        trnMetrics.recordDuplicateDetected("DIVI", withinDays = 5)

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES).count())
            .isEqualTo(4.0)
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES_WITHIN_DAYS, TAG_DAYS, "0").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES_WITHIN_DAYS, TAG_DAYS, "5").count())
            .isEqualTo(2.0)
        assertThat(meterRegistry.counter(METRIC_TRN_DUPLICATES_WITHIN_DAYS, TAG_DAYS, "20").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record transaction ignored with reason`() {
        // When
        trnMetrics.recordTrnIgnored("portfolio_verification_failed")

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_IGNORED).count())
            .isEqualTo(1.0)
        assertThat(
            meterRegistry
                .counter(
                    METRIC_TRN_IGNORED_BY_REASON,
                    "reason",
                    "portfolio_verification_failed"
                ).count()
        ).isEqualTo(1.0)
    }

    @Test
    fun `should record multiple ignore reasons separately`() {
        // When
        trnMetrics.recordTrnIgnored("portfolio_verification_failed")
        trnMetrics.recordTrnIgnored("invalid_asset")
        trnMetrics.recordTrnIgnored("portfolio_verification_failed")

        // Then
        assertThat(meterRegistry.counter(METRIC_TRN_IGNORED).count())
            .isEqualTo(3.0)
        assertThat(
            meterRegistry
                .counter(
                    METRIC_TRN_IGNORED_BY_REASON,
                    "reason",
                    "portfolio_verification_failed"
                ).count()
        ).isEqualTo(2.0)
        assertThat(meterRegistry.counter(METRIC_TRN_IGNORED_BY_REASON, "reason", "invalid_asset").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record transaction error with type`() {
        // When
        trnMetrics.recordTrnError("database_write_failed")

        // Then
        assertThat(meterRegistry.counter("beancounter.trn.errors").count())
            .isEqualTo(1.0)
        assertThat(
            meterRegistry.counter("beancounter.trn.errors.by_type", "error_type", "database_write_failed").count()
        ).isEqualTo(1.0)
    }

    @Test
    fun `should record dividend detected in price data`() {
        // When
        trnMetrics.recordDividendDetected()

        // Then
        assertThat(meterRegistry.counter("beancounter.price.dividends.detected").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record split detected in price data`() {
        // When
        trnMetrics.recordSplitDetected()

        // Then
        assertThat(meterRegistry.counter("beancounter.price.splits.detected").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record corporate event published`() {
        // When
        trnMetrics.recordCorporateEventPublished("DIVI")

        // Then
        assertThat(meterRegistry.counter(METRIC_EVENTS_PUBLISHED).count())
            .isEqualTo(1.0)
        assertThat(
            meterRegistry
                .counter(
                    METRIC_EVENTS_PUBLISHED_BY_TYPE,
                    "type",
                    "DIVI"
                ).count()
        ).isEqualTo(1.0)
    }

    @Test
    fun `should accumulate corporate events published`() {
        // When
        trnMetrics.recordCorporateEventPublished("DIVI")
        trnMetrics.recordCorporateEventPublished("SPLIT")
        trnMetrics.recordCorporateEventPublished("DIVI")

        // Then
        assertThat(meterRegistry.counter(METRIC_EVENTS_PUBLISHED).count())
            .isEqualTo(3.0)
        assertThat(
            meterRegistry
                .counter(
                    METRIC_EVENTS_PUBLISHED_BY_TYPE,
                    "type",
                    "DIVI"
                ).count()
        ).isEqualTo(2.0)
        assertThat(
            meterRegistry
                .counter(
                    METRIC_EVENTS_PUBLISHED_BY_TYPE,
                    "type",
                    "SPLIT"
                ).count()
        ).isEqualTo(1.0)
    }

    @Test
    fun `should time transaction import operation`() {
        // When
        val result =
            trnMetrics.timeTransactionImport {
                Thread.sleep(10) // Simulate work
                "completed"
            }

        // Then
        assertThat(result).isEqualTo("completed")
        val timer = meterRegistry.timer(METRIC_TRN_IMPORT_TIME)
        assertThat(timer.count()).isEqualTo(1)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(5.0)
    }

    @Test
    fun `should record import time manually`() {
        // When
        trnMetrics.recordImportTime(150L)

        // Then
        val timer = meterRegistry.timer(METRIC_TRN_IMPORT_TIME)
        assertThat(timer.count()).isEqualTo(1)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(150.0)
    }

    @Test
    fun `should handle exceptions in timed operations`() {
        // When/Then
        try {
            trnMetrics.timeTransactionImport {
                @Suppress("TooGenericExceptionThrown")
                throw RuntimeException("test exception")
            }
        } catch (e: RuntimeException) {
            assertThat(e.message).isEqualTo("test exception")
        }

        // Timer should still record the attempt
        val timer = meterRegistry.timer(METRIC_TRN_IMPORT_TIME)
        assertThat(timer.count()).isEqualTo(1)
    }

    @Test
    fun `should track complete flow from detection to write`() {
        // Given - Simulate complete flow
        trnMetrics.recordDividendDetected()
        trnMetrics.recordCorporateEventPublished("DIVI")
        trnMetrics.recordTrnEventReceived("DIVI")
        trnMetrics.recordTrnWritten("DIVI", 2)

        // Then
        assertThat(meterRegistry.counter("beancounter.price.dividends.detected").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_EVENTS_PUBLISHED).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_EVENTS_RECEIVED).count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter(METRIC_TRN_WRITTEN).count())
            .isEqualTo(2.0)
    }
}