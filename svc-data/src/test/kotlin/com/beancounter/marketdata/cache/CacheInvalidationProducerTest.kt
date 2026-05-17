package com.beancounter.marketdata.cache

import com.beancounter.common.contracts.CacheChangeType
import com.beancounter.common.contracts.CacheInvalidationEvent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.MessagingException
import java.time.LocalDate

internal class CacheInvalidationProducerTest {
    private val streamBridge: StreamBridge = mock()
    private val producer = CacheInvalidationProducer(streamBridge)

    @Test
    fun `sendPriceHistoryEvent emits PRICE_HISTORY event with asset and date`() {
        whenever(streamBridge.send(eq(BINDING), any<CacheInvalidationEvent>())).thenReturn(true)
        val fromDate = LocalDate.of(2024, 5, 1)

        producer.sendPriceHistoryEvent("asset-1", fromDate)

        val captor = argumentCaptor<CacheInvalidationEvent>()
        verify(streamBridge).send(eq(BINDING), captor.capture())
        assertThat(captor.firstValue.changeType).isEqualTo(CacheChangeType.PRICE_HISTORY)
        assertThat(captor.firstValue.assetId).isEqualTo("asset-1")
        assertThat(captor.firstValue.fromDate).isEqualTo(fromDate)
    }

    @Test
    fun `sendTransactionEvent emits TRANSACTION event with portfolio and date`() {
        whenever(streamBridge.send(eq(BINDING), any<CacheInvalidationEvent>())).thenReturn(true)

        producer.sendTransactionEvent("portfolio-1", LocalDate.of(2024, 1, 1))

        val captor = argumentCaptor<CacheInvalidationEvent>()
        verify(streamBridge).send(eq(BINDING), captor.capture())
        assertThat(captor.firstValue.changeType).isEqualTo(CacheChangeType.TRANSACTION)
        assertThat(captor.firstValue.portfolioId).isEqualTo("portfolio-1")
    }

    @Test
    fun `streamBridge not invoked when stream disabled`() {
        producer.streamEnabled = false

        producer.sendPriceEvent(LocalDate.now())
        producer.sendFxEvent(LocalDate.now())
        producer.sendPriceHistoryEvent("asset-1", LocalDate.now())

        verify(streamBridge, never()).send(eq(BINDING), any<CacheInvalidationEvent>())
    }

    @Test
    fun `MessagingException swallowed so a broker outage does not propagate`() {
        whenever(streamBridge.send(eq(BINDING), any<CacheInvalidationEvent>()))
            .thenThrow(MessagingException("broker down"))

        // Must not throw — the call site is fire-and-forget.
        producer.sendPriceHistoryEvent("asset-1", LocalDate.now())
        verify(streamBridge).send(eq(BINDING), any<CacheInvalidationEvent>())
    }

    private companion object {
        const val BINDING = "cacheInvalidation-out-0"
    }
}