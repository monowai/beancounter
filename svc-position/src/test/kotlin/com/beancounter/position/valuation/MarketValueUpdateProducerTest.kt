package com.beancounter.position.valuation

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Positions
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.utils.TestHelpers
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.catchThrowable
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.Mockito.mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.slf4j.LoggerFactory
import org.springframework.cloud.stream.function.StreamBridge
import org.springframework.messaging.Message

/**
 * Test suite for MarketValueUpdateProducer to ensure proper message publishing via Spring Cloud Stream.
 *
 * This class tests:
 * - Portfolio message publishing via StreamBridge
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
        marketValueUpdateProducer = MarketValueUpdateProducer(streamBridge, DateUtils())
        lenient().`when`(streamBridge.send(any(), any<Message<*>>())).thenReturn(true)
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
    fun `should handle different portfolio types`() {
        // Given
        val differentPortfolio = TestHelpers.createTestPortfolio("different-portfolio", "EUR")

        // When
        marketValueUpdateProducer.sendMessage(differentPortfolio)

        // Then
        verify(streamBridge).send(
            eq("portfolioMarketValue-out-0"),
            ArgumentMatchers.argThat<Message<Portfolio>> { message ->
                message.payload == differentPortfolio
            }
        )
    }

    @Test
    fun `a payload that cannot be built is logged and does not fail the valuation`() {
        val dateUtils: DateUtils = mock()
        whenever(dateUtils.isToday(any())).thenReturn(true)
        whenever(dateUtils.getDate(any<String>())).thenThrow(IllegalArgumentException("bad asAt"))
        val producer = MarketValueUpdateProducer(streamBridge, dateUtils)
        val positions = Positions(testPortfolio)

        val logger = LoggerFactory.getLogger(MarketValueUpdateProducer::class.java) as Logger
        val appender = ListAppender<ILoggingEvent>().apply { start() }
        logger.addAppender(appender)
        val thrown =
            try {
                catchThrowable { producer.publish(positions) }
            } finally {
                logger.detachAppender(appender)
            }

        assertThat(thrown).isNull()
        verify(streamBridge, never()).send(any(), any<Message<*>>())
        assertThat(appender.list).anySatisfy {
            assertThat(it.level).isEqualTo(Level.ERROR)
            assertThat(it.formattedMessage).contains(testPortfolio.code)
        }
    }
}