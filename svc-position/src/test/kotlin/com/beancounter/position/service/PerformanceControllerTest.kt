package com.beancounter.position.service

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.AggregatedPerformanceData
import com.beancounter.common.contracts.AggregatedPerformanceDataPoint
import com.beancounter.common.contracts.AggregatedPerformanceRequest
import com.beancounter.common.contracts.AggregatedPerformanceResponse
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.contracts.PerformanceResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.cache.PerformanceCacheService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class PerformanceControllerTest {
    @Mock
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Mock
    private lateinit var performanceService: PerformanceService

    @Mock
    private lateinit var performanceCacheService: PerformanceCacheService

    @Mock
    private lateinit var benchmarkService: BenchmarkService

    @Mock
    private lateinit var staticService: StaticService

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
        controller =
            PerformanceController(
                portfolioServiceClient,
                performanceService,
                performanceCacheService,
                benchmarkService,
                staticService
            )
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

    @Test
    fun `getBenchmark delegates to BenchmarkService and returns its response`() {
        val benchmarkResponse =
            PerformanceResponse(
                PerformanceData(
                    currency = USD,
                    series =
                        listOf(
                            PerformanceDataPoint(
                                date = LocalDate.of(2024, 1, 1),
                                growthOf1000 = BigDecimal("1000"),
                                marketValue = BigDecimal.ZERO,
                                netContributions = BigDecimal.ZERO,
                                cumulativeReturn = BigDecimal.ZERO
                            ),
                            PerformanceDataPoint(
                                date = LocalDate.of(2024, 6, 1),
                                growthOf1000 = BigDecimal("1075"),
                                marketValue = BigDecimal.ZERO,
                                netContributions = BigDecimal.ZERO,
                                cumulativeReturn = BigDecimal("0.075")
                            )
                        )
                )
            )
        whenever(portfolioServiceClient.getPortfolioByCode("TEST")).thenReturn(portfolio)
        whenever(benchmarkService.benchmark(portfolio, "^GSPC", 12)).thenReturn(benchmarkResponse)

        val result = controller.getBenchmark("TEST", "^GSPC", 12)

        assertThat(result.data.series).hasSize(2)
        assertThat(
            result.data.series
                .last()
                .growthOf1000
        ).isEqualByComparingTo(BigDecimal("1075"))
        verify(benchmarkService).benchmark(portfolio, "^GSPC", 12)
    }

    @Test
    fun `aggregate resolves portfolios and delegates to service with display currency`() {
        val p1 = portfolio.copy(id = "p1", code = "P1")
        val p2 = portfolio.copy(id = "p2", code = "P2")
        val series =
            listOf(
                AggregatedPerformanceDataPoint(
                    date = LocalDate.of(2025, 1, 1),
                    growthOf1000 = BigDecimal("1000"),
                    cumulativeReturn = BigDecimal.ZERO,
                    marketValue = BigDecimal("0"),
                    netContributions = BigDecimal("0"),
                    lifetimeContributions = BigDecimal("0"),
                    cumulativeDividends = BigDecimal("0"),
                    investmentGain = BigDecimal("0")
                ),
                AggregatedPerformanceDataPoint(
                    date = LocalDate.of(2025, 12, 1),
                    growthOf1000 = BigDecimal("1140"),
                    cumulativeReturn = BigDecimal("0.14"),
                    marketValue = BigDecimal("114000"),
                    netContributions = BigDecimal("10000"),
                    lifetimeContributions = BigDecimal("110000"),
                    cumulativeDividends = BigDecimal("0"),
                    investmentGain = BigDecimal("4000")
                )
            )
        val expected =
            AggregatedPerformanceResponse(
                AggregatedPerformanceData(currency = USD, series = series)
            )
        whenever(staticService.getCurrency("USD")).thenReturn(USD)
        whenever(portfolioServiceClient.getPortfolioByCode("P1")).thenReturn(p1)
        whenever(portfolioServiceClient.getPortfolioByCode("P2")).thenReturn(p2)
        whenever(performanceService.aggregate(listOf(p1, p2), 12, USD)).thenReturn(expected)

        val result =
            controller.aggregate(
                AggregatedPerformanceRequest(
                    portfolioCodes = listOf("P1", "P2"),
                    months = 12,
                    displayCurrency = "USD"
                )
            )

        assertThat(result.data.series).hasSize(2)
        assertThat(
            result.data.series
                .last()
                .cumulativeReturn
        ).isEqualByComparingTo(BigDecimal("0.14"))
        verify(performanceService).aggregate(listOf(p1, p2), 12, USD)
    }

    @Test
    fun `aggregate rejects unknown display currency`() {
        whenever(staticService.getCurrency("XYZ")).thenReturn(null)

        assertThatThrownBy {
            controller.aggregate(
                AggregatedPerformanceRequest(
                    portfolioCodes = listOf("P1"),
                    months = 12,
                    displayCurrency = "XYZ"
                )
            )
        }.isInstanceOf(BusinessException::class.java)
            .hasMessageContaining("XYZ")
    }

    @Test
    fun `aggregate with empty portfolio list short-circuits to service empty response`() {
        whenever(staticService.getCurrency("USD")).thenReturn(USD)
        whenever(performanceService.aggregate(emptyList(), 6, USD))
            .thenReturn(AggregatedPerformanceResponse(AggregatedPerformanceData(USD)))

        val result =
            controller.aggregate(
                AggregatedPerformanceRequest(
                    portfolioCodes = emptyList(),
                    months = 6,
                    displayCurrency = "USD"
                )
            )

        assertThat(result.data.series).isEmpty()
        verify(performanceService).aggregate(emptyList(), 6, USD)
    }

    @Test
    fun `resetCache invalidates portfolio and returns ok`() {
        whenever(portfolioServiceClient.getPortfolioByCode("TEST")).thenReturn(portfolio)

        val result = controller.resetCache("TEST")

        verify(performanceCacheService).invalidatePortfolio("test-pf")
        assertThat(result["status"]).isEqualTo("ok")
        assertThat(result["portfolio"]).isEqualTo("TEST")
    }

    @Test
    fun `resolves managed portfolio by id when by-code lookup fails`() {
        // Managed portfolios are owned by another SystemUser; the holdings
        // page sends `portfolio.id` to the performance endpoint. svc-data's
        // `findByCode(code, owner)` is owner-scoped and would 404, while
        // `find(id)` applies `canView` and succeeds for shared portfolios.
        val managedId = "managed-pf-uuid"
        whenever(portfolioServiceClient.getPortfolioById(managedId)).thenReturn(portfolio)
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
        whenever(performanceService.calculate(portfolio, 12)).thenReturn(expectedResponse)

        val result = controller.getPerformance(managedId, 12)

        assertThat(result.data.series).hasSize(1)
        verify(performanceService).calculate(portfolio, 12)
    }
}