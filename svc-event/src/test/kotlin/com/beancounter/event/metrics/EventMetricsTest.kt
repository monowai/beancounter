package com.beancounter.event.metrics

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EventMetricsTest {
    private lateinit var meterRegistry: MeterRegistry
    private lateinit var eventMetrics: EventMetrics

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        eventMetrics = EventMetrics(meterRegistry)
    }

    @Test
    fun `should record dividend event received`() {
        // When
        eventMetrics.recordEventReceived(isDividend = true)

        // Then
        assertThat(meterRegistry.counter("beancounter.event.received", "type", "all").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter("beancounter.event.received", "type", "dividend").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record split event received`() {
        // When
        eventMetrics.recordEventReceived(isDividend = false)

        // Then
        assertThat(meterRegistry.counter("beancounter.event.received", "type", "all").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter("beancounter.event.received", "type", "split").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record multiple events received`() {
        // When
        eventMetrics.recordEventReceived(isDividend = true)
        eventMetrics.recordEventReceived(isDividend = true)
        eventMetrics.recordEventReceived(isDividend = false)

        // Then
        assertThat(meterRegistry.counter("beancounter.event.received", "type", "all").count())
            .isEqualTo(3.0)
        assertThat(meterRegistry.counter("beancounter.event.received", "type", "dividend").count())
            .isEqualTo(2.0)
        assertThat(meterRegistry.counter("beancounter.event.received", "type", "split").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record event processed with portfolio and transaction counts`() {
        // When
        eventMetrics.recordEventProcessed(portfolioCount = 3, transactionCount = 5)

        // Then
        assertThat(meterRegistry.counter("beancounter.event.processed").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter("beancounter.event.portfolios.analyzed").count())
            .isEqualTo(3.0)
        assertThat(meterRegistry.counter("beancounter.event.transactions.created").count())
            .isEqualTo(5.0)
    }

    @Test
    fun `should accumulate portfolio and transaction counts`() {
        // When
        eventMetrics.recordEventProcessed(portfolioCount = 2, transactionCount = 3)
        eventMetrics.recordEventProcessed(portfolioCount = 4, transactionCount = 7)

        // Then
        assertThat(meterRegistry.counter("beancounter.event.processed").count())
            .isEqualTo(2.0)
        assertThat(meterRegistry.counter("beancounter.event.portfolios.analyzed").count())
            .isEqualTo(6.0)
        assertThat(meterRegistry.counter("beancounter.event.transactions.created").count())
            .isEqualTo(10.0)
    }

    @Test
    fun `should record event ignored with reason`() {
        // When
        eventMetrics.recordEventIgnored("no_positions")

        // Then
        assertThat(meterRegistry.counter("beancounter.event.ignored").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter("beancounter.event.ignored.by_reason", "reason", "no_positions").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record multiple ignored reasons separately`() {
        // When
        eventMetrics.recordEventIgnored("no_positions")
        eventMetrics.recordEventIgnored("future_dated")
        eventMetrics.recordEventIgnored("no_positions")

        // Then
        assertThat(meterRegistry.counter("beancounter.event.ignored").count())
            .isEqualTo(3.0)
        assertThat(meterRegistry.counter("beancounter.event.ignored.by_reason", "reason", "no_positions").count())
            .isEqualTo(2.0)
        assertThat(meterRegistry.counter("beancounter.event.ignored.by_reason", "reason", "future_dated").count())
            .isEqualTo(1.0)
    }

    @Test
    fun `should record event error with type`() {
        // When
        eventMetrics.recordEventError("position_query_failed")

        // Then
        assertThat(meterRegistry.counter("beancounter.event.errors").count())
            .isEqualTo(1.0)
        assertThat(
            meterRegistry.counter("beancounter.event.errors.by_type", "error_type", "position_query_failed").count()
        ).isEqualTo(1.0)
    }

    @Test
    fun `should record backfill operation`() {
        // When
        eventMetrics.recordBackfillOperation(eventsLoaded = 15)

        // Then
        assertThat(meterRegistry.counter("beancounter.event.backfill.operations").count())
            .isEqualTo(1.0)
        assertThat(meterRegistry.counter("beancounter.event.backfill.events.loaded").count())
            .isEqualTo(15.0)
    }

    @Test
    fun `should accumulate backfill events loaded`() {
        // When
        eventMetrics.recordBackfillOperation(eventsLoaded = 10)
        eventMetrics.recordBackfillOperation(eventsLoaded = 5)

        // Then
        assertThat(meterRegistry.counter("beancounter.event.backfill.operations").count())
            .isEqualTo(2.0)
        assertThat(meterRegistry.counter("beancounter.event.backfill.events.loaded").count())
            .isEqualTo(15.0)
    }

    @Test
    fun `should time event processing operation`() {
        // When
        val result =
            eventMetrics.timeEventProcessing {
                Thread.sleep(10) // Simulate work
                "completed"
            }

        // Then
        assertThat(result).isEqualTo("completed")
        val timer = meterRegistry.timer("beancounter.event.processing.time")
        assertThat(timer.count()).isEqualTo(1)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isGreaterThan(5.0)
    }

    @Test
    fun `should record processing time manually`() {
        // When
        eventMetrics.recordProcessingTime(100L)

        // Then
        val timer = meterRegistry.timer("beancounter.event.processing.time")
        assertThat(timer.count()).isEqualTo(1)
        assertThat(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS)).isEqualTo(100.0)
    }

    @Test
    fun `should handle exceptions in timed operations`() {
        // When/Then
        try {
            eventMetrics.timeEventProcessing {
                throw IllegalStateException("test exception")
            }
        } catch (e: IllegalStateException) {
            assertThat(e.message).isEqualTo("test exception")
        }

        // Timer should still record the attempt
        val timer = meterRegistry.timer("beancounter.event.processing.time")
        assertThat(timer.count()).isEqualTo(1)
    }
}