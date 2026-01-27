package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import java.math.BigDecimal

/**
 * Unit test for PortfolioStreamConsumer.
 * Tests the portfolioConsumer function bean logic directly.
 */
class PortfolioStreamConsumerTest {
    @Test
    fun `portfolioConsumer should call portfolioService maintain with lastUpdated set`() {
        // Given
        val portfolioService = mock<PortfolioService>()
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

        // Then - verify maintain is called with a portfolio that has lastUpdated set
        val captor = argumentCaptor<Portfolio>()
        verify(portfolioService).maintain(captor.capture())

        val savedPortfolio = captor.firstValue
        assertThat(savedPortfolio.id).isEqualTo(portfolio.id)
        assertThat(savedPortfolio.code).isEqualTo(portfolio.code)
        assertThat(savedPortfolio.name).isEqualTo(portfolio.name)
        assertThat(savedPortfolio.lastUpdated).isNotNull()
    }
}