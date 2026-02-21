package com.beancounter.position.service

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.contracts.PerformanceResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PerformanceControllerTest {
    @Mock
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Mock
    private lateinit var performanceService: PerformanceService

    private lateinit var controller: PerformanceController

    private val portfolio =
        Portfolio(
            id = "test-pf",
            code = "TEST",
            name = "Test Portfolio",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setup() {
        controller = PerformanceController(portfolioServiceClient, performanceService)
    }

    @Test
    fun `returns performance response for valid portfolio`() {
        val expectedResponse =
            PerformanceResponse(
                PerformanceData(
                    currency = USD,
                    series =
                        listOf(
                            PerformanceDataPoint(
                                date = LocalDate.of(2024, 1, 1),
                                growthOf1000 = BigDecimal("1000"),
                                marketValue = BigDecimal("10000"),
                                netContributions = BigDecimal("10000"),
                                cumulativeReturn = BigDecimal.ZERO
                            )
                        )
                )
            )
        whenever(portfolioServiceClient.getPortfolioByCode("TEST")).thenReturn(portfolio)
        whenever(performanceService.calculate(portfolio, 12)).thenReturn(expectedResponse)

        val result = controller.getPerformance("TEST", 12)

        assertThat(result.data.currency.code).isEqualTo("USD")
        assertThat(result.data.series).hasSize(1)
        assertThat(result.data.series[0].growthOf1000).isEqualByComparingTo(BigDecimal("1000"))
    }

    @Test
    fun `passes months parameter correctly`() {
        val emptyResponse =
            PerformanceResponse(
                PerformanceData(currency = Currency("GBP"), series = emptyList())
            )
        val gbpPortfolio = portfolio.copy(base = Currency("GBP"))
        whenever(portfolioServiceClient.getPortfolioByCode("GBP_PF")).thenReturn(gbpPortfolio)
        whenever(performanceService.calculate(gbpPortfolio, 6)).thenReturn(emptyResponse)

        val result = controller.getPerformance("GBP_PF", 6)

        assertThat(result.data.currency.code).isEqualTo("GBP")
        assertThat(result.data.series).isEmpty()
    }
}