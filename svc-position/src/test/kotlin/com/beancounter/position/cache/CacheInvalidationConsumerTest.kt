package com.beancounter.position.cache

import com.beancounter.common.contracts.CacheChangeType
import com.beancounter.common.contracts.CacheInvalidationEvent
import com.beancounter.position.schedule.PortfolioRevaluationTrigger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CacheInvalidationConsumerTest {
    @Mock
    private lateinit var cacheService: PerformanceCacheService

    @Mock
    private lateinit var revaluationTrigger: PortfolioRevaluationTrigger

    @Test
    fun `TRANSACTION event invalidates portfolio from date`() {
        val consumer = CacheInvalidationConsumer(cacheService)
        val handler = consumer.performanceCacheInvalidation()
        val event =
            CacheInvalidationEvent(
                changeType = CacheChangeType.TRANSACTION,
                portfolioId = "pf-123",
                fromDate = LocalDate.of(2024, 6, 15)
            )

        handler.accept(event)

        verify(cacheService).invalidateFrom("pf-123", LocalDate.of(2024, 6, 15))
    }

    @Test
    fun `PRICE event invalidates all portfolios on date`() {
        val consumer = CacheInvalidationConsumer(cacheService)
        val handler = consumer.performanceCacheInvalidation()
        val event =
            CacheInvalidationEvent(
                changeType = CacheChangeType.PRICE,
                fromDate = LocalDate.of(2024, 6, 15)
            )

        handler.accept(event)

        verify(cacheService).invalidateOnDate(LocalDate.of(2024, 6, 15))
    }

    @Test
    fun `FX event invalidates all portfolios on date`() {
        val consumer = CacheInvalidationConsumer(cacheService)
        val handler = consumer.performanceCacheInvalidation()
        val event =
            CacheInvalidationEvent(
                changeType = CacheChangeType.FX,
                fromDate = LocalDate.of(2024, 6, 15)
            )

        handler.accept(event)

        verify(cacheService).invalidateOnDate(LocalDate.of(2024, 6, 15))
    }

    @Test
    fun `PRICE_HISTORY event invalidates snapshots from date`() {
        val consumer = CacheInvalidationConsumer(cacheService)
        val handler = consumer.performanceCacheInvalidation()
        val event =
            CacheInvalidationEvent(
                changeType = CacheChangeType.PRICE_HISTORY,
                assetId = "asset-1",
                fromDate = LocalDate.of(2020, 1, 1)
            )

        handler.accept(event)

        verify(cacheService).invalidateFromDate(LocalDate.of(2020, 1, 1))
    }

    @Test
    fun `PRICE event schedules a debounced revaluation when trigger is wired`() {
        val consumer = CacheInvalidationConsumer(cacheService)
        consumer.setRevaluationTrigger(revaluationTrigger)
        val handler = consumer.performanceCacheInvalidation()
        val event =
            CacheInvalidationEvent(
                changeType = CacheChangeType.PRICE,
                fromDate = LocalDate.of(2024, 6, 15)
            )

        handler.accept(event)

        verify(revaluationTrigger).scheduleRevaluation(eq("cache:PRICE"))
    }

    @Test
    fun `FX event schedules a debounced revaluation when trigger is wired`() {
        val consumer = CacheInvalidationConsumer(cacheService)
        consumer.setRevaluationTrigger(revaluationTrigger)
        val handler = consumer.performanceCacheInvalidation()
        val event =
            CacheInvalidationEvent(
                changeType = CacheChangeType.FX,
                fromDate = LocalDate.of(2024, 6, 15)
            )

        handler.accept(event)

        verify(revaluationTrigger).scheduleRevaluation(eq("cache:FX"))
    }

    @Test
    fun `TRANSACTION event does not trigger revaluation`() {
        val consumer = CacheInvalidationConsumer(cacheService)
        consumer.setRevaluationTrigger(revaluationTrigger)
        val handler = consumer.performanceCacheInvalidation()
        val event =
            CacheInvalidationEvent(
                changeType = CacheChangeType.TRANSACTION,
                portfolioId = "pf-123",
                fromDate = LocalDate.of(2024, 6, 15)
            )

        handler.accept(event)

        verify(revaluationTrigger, never()).scheduleRevaluation(any())
    }
}