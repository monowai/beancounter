package com.beancounter.position.cache

import com.beancounter.common.contracts.CacheChangeType
import com.beancounter.common.contracts.CacheInvalidationEvent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class CacheInvalidationConsumerTest {
    @Mock
    private lateinit var cacheService: PerformanceCacheService

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
}