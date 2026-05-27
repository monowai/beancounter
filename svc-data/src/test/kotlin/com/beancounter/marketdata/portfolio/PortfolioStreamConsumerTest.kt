package com.beancounter.marketdata.portfolio

import com.beancounter.common.model.Portfolio
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal

/**
 * Unit test for PortfolioStreamConsumer.
 * Tests the portfolioConsumer function bean logic directly.
 */
class PortfolioStreamConsumerTest {
    @Test
    fun `portfolioConsumer applies valuation but preserves identity from DB`() {
        val portfolioService = mock<PortfolioService>()
        val consumer = PortfolioStreamConsumer(portfolioService)

        // DB state has the user's most recent edit (cashPortfolioId set, custom name).
        val existing =
            Portfolio(
                id = "1",
                code = "TEST",
                name = "User Renamed",
                cashPortfolioId = "FUNDING-1"
            )
        whenever(portfolioService.findOrNull("1")).thenReturn(existing)

        // Stream payload from bc-position carries stale identity but fresh valuation.
        val incoming =
            Portfolio(
                id = "1",
                code = "STALE",
                name = "Stale Name",
                marketValue = BigDecimal.TEN,
                irr = BigDecimal.ONE,
                cashPortfolioId = null
            )

        consumer.portfolioConsumer().accept(incoming)

        val captor = argumentCaptor<Portfolio>()
        verify(portfolioService).maintain(captor.capture())

        val saved = captor.firstValue
        // Identity comes from the DB — stale stream values are ignored.
        assertThat(saved.code).isEqualTo("TEST")
        assertThat(saved.name).isEqualTo("User Renamed")
        assertThat(saved.cashPortfolioId).isEqualTo("FUNDING-1")
        // Valuation comes from the stream.
        assertThat(saved.marketValue).isEqualTo(BigDecimal.TEN)
        assertThat(saved.irr).isEqualTo(BigDecimal.ONE)
        assertThat(saved.lastUpdated).isNotNull()
    }

    @Test
    fun `portfolioConsumer skips when portfolio no longer exists`() {
        val portfolioService = mock<PortfolioService>()
        val consumer = PortfolioStreamConsumer(portfolioService)
        whenever(portfolioService.findOrNull("gone")).thenReturn(null)

        consumer.portfolioConsumer().accept(Portfolio(id = "gone"))

        verify(portfolioService).findOrNull("gone")
        verify(portfolioService, never()).maintain(any())
    }
}