package com.beancounter.position.service

import com.beancounter.common.model.Portfolio
import com.beancounter.position.utils.TestHelpers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.Message

/**
 * Test suite for MarketValueUpdateProducer to ensure proper message publishing via Spring Cloud Stream.
 *
 * This class tests:
 * - Portfolio message publishing via StreamBridge
 * - Message key handling
 * - StreamBridge interaction
 * - Message payload handling
 *
 * Tests verify that the MarketValueUpdateProducer correctly
 * publishes portfolio updates via the configured binding.
 */
@ExtendWith(MockitoExtension::class)
class MarketValueUpdateProducerTest {
    @Mock
    private lateinit var streamBridge: StreamBridge

    private lateinit var marketValueUpdateProducer: MarketValueUpdateProducer

    private lateinit var testPortfolio: Portfolio

    @BeforeEach
    fun setUp() {
        testPortfolio = TestHelpers.createTestPortfolio("test-portfolio")
        marketValueUpdateProducer = MarketValueUpdateProducer(streamBridge)
        whenever(streamBridge.send(any(), any<Message<*>>())).thenReturn(true)
    }

    @Test
    fun `should send portfolio message via StreamBridge`() {
        // When
        marketValueUpdateProducer.sendMessage(testPortfolio)

        // Then
        verify(streamBridge).send(
            eq("portfolioMarketValue-out-0"),
            ArgumentMatchers.argThat<Message<Portfolio>> { message ->
                message.payload == testPortfolio
            }
        )
    }

    @Test
    fun `should use portfolio ID as message key`() {
        // Given
        val portfolioId = "custom-portfolio-id"
        val customPortfolio = TestHelpers.createTestPortfolio(portfolioId)

        // When
        marketValueUpdateProducer.sendMessage(customPortfolio)

        // Then
        verify(streamBridge).send(
            eq("portfolioMarketValue-out-0"),
            ArgumentMatchers.argThat<Message<Portfolio>> { message ->
                message.payload == customPortfolio &&
                    message.headers[KafkaHeaders.KEY] == portfolioId
            }
        )
    }

    @Test
    fun `should set Kafka message key header`() {
        // When
        marketValueUpdateProducer.sendMessage(testPortfolio)

        // Then
        verify(streamBridge).send(
            eq("portfolioMarketValue-out-0"),
            ArgumentMatchers.argThat<Message<Portfolio>> { message ->
                message.headers.containsKey(KafkaHeaders.KEY) &&
                    message.headers[KafkaHeaders.KEY] == testPortfolio.id
            }
        )
    }

    @Test
    fun `should handle different portfolio types`() {
        // Given
        val differentPortfolio = TestHelpers.createTestPortfolio("different-portfolio", "EUR")

        // When
        marketValueUpdateProducer.sendMessage(differentPortfolio)

        // Then
        verify(streamBridge).send(
            eq("portfolioMarketValue-out-0"),
            ArgumentMatchers.argThat<Message<Portfolio>> { message ->
                message.payload == differentPortfolio &&
                    message.headers[KafkaHeaders.KEY] == differentPortfolio.id
            }
        )
    }
}