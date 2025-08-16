package com.beancounter.position.service

import com.beancounter.common.model.Portfolio
import com.beancounter.position.utils.TestHelpers
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

/**
 * Test suite for MarketValueUpdateProducer to ensure proper Kafka message publishing.
 *
 * This class tests:
 * - Portfolio message publishing to Kafka
 * - Topic configuration and message routing
 * - Kafka template interaction
 * - Message payload handling
 *
 * Tests verify that the MarketValueUpdateProducer correctly
 * publishes portfolio updates to the configured Kafka topic.
 */
@ExtendWith(MockitoExtension::class)
class MarketValueUpdateProducerTest {
    @Mock
    private lateinit var kafkaTemplate: KafkaTemplate<String, Portfolio>

    @Mock
    private lateinit var sendResult: SendResult<String, Portfolio>

    private lateinit var marketValueUpdateProducer: MarketValueUpdateProducer

    private lateinit var testPortfolio: Portfolio
    private val testTopicName = "test-pos-mv-topic"

    @BeforeEach
    fun setUp() {
        testPortfolio = TestHelpers.createTestPortfolio("test-portfolio")
        marketValueUpdateProducer = MarketValueUpdateProducer(kafkaTemplate, testTopicName)
    }

    @Test
    fun `should send portfolio message to Kafka topic`() {
        // Given
        val future = CompletableFuture.completedFuture(sendResult)
        whenever(kafkaTemplate.send(testTopicName, testPortfolio.id, testPortfolio))
            .thenReturn(future)

        // When
        marketValueUpdateProducer.sendMessage(testPortfolio)

        // Then
        verify(kafkaTemplate).send(testTopicName, testPortfolio.id, testPortfolio)
    }

    @Test
    fun `should use portfolio ID as message key`() {
        // Given
        val portfolioId = "custom-portfolio-id"
        val customPortfolio = TestHelpers.createTestPortfolio(portfolioId)
        val future = CompletableFuture.completedFuture(sendResult)

        whenever(kafkaTemplate.send(testTopicName, portfolioId, customPortfolio))
            .thenReturn(future)

        // When
        marketValueUpdateProducer.sendMessage(customPortfolio)

        // Then
        verify(kafkaTemplate).send(testTopicName, portfolioId, customPortfolio)
    }

    @Test
    fun `should use configured topic name`() {
        // Given
        val customTopicName = "custom-topic-name"
        val customProducer = MarketValueUpdateProducer(kafkaTemplate, customTopicName)
        val future = CompletableFuture.completedFuture(sendResult)

        whenever(kafkaTemplate.send(customTopicName, testPortfolio.id, testPortfolio))
            .thenReturn(future)

        // When
        customProducer.sendMessage(testPortfolio)

        // Then
        verify(kafkaTemplate).send(customTopicName, testPortfolio.id, testPortfolio)
    }

    @Test
    fun `should handle different portfolio types`() {
        // Given
        val differentPortfolio = TestHelpers.createTestPortfolio("different-portfolio", "EUR")
        val future = CompletableFuture.completedFuture(sendResult)

        whenever(kafkaTemplate.send(testTopicName, differentPortfolio.id, differentPortfolio))
            .thenReturn(future)

        // When
        marketValueUpdateProducer.sendMessage(differentPortfolio)

        // Then
        verify(kafkaTemplate).send(testTopicName, differentPortfolio.id, differentPortfolio)
    }
}