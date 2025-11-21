package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import java.math.BigDecimal

/**
 * Unit test for PortfolioStreamConsumer.
 * Tests the portfolioConsumer function bean logic directly.
 */
class PortfolioStreamConsumerTest {
    @Test
    fun `portfolioConsumer should call portfolioService maintain`() {
        // Given
        val portfolioService = mock(PortfolioService::class.java)
        val consumer = PortfolioStreamConsumer(portfolioService)

        val portfolio =
            Portfolio(
                id = "1",
                code = "TEST",
                name = "Test Portfolio",
                marketValue = BigDecimal.TEN,
                irr = BigDecimal.ONE
            )

        // When
        consumer.portfolioConsumer().accept(portfolio)

        // Then
        verify(portfolioService).maintain(portfolio)
    }
}