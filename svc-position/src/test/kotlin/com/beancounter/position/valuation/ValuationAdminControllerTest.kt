package com.beancounter.position.valuation

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.web.client.RestClientException
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class ValuationAdminControllerTest {
    @Mock
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Mock
    private lateinit var valuationService: Valuation

    private val dateUtils = DateUtils()
    private lateinit var controller: ValuationAdminController

    @BeforeEach
    fun setUp() {
        controller = ValuationAdminController(portfolioServiceClient, valuationService, dateUtils, 24)
    }

    @Test
    fun `should report zero counts when there are no portfolios`() {
        whenever(portfolioServiceClient.portfolios).thenReturn(PortfoliosResponse(emptyList()))

        val result = controller.revalueStalePortfolios(force = false)

        assertThat(result).isEqualTo(
            ValuationAdminController.RevalueResponse(
                totalPortfolios = 0,
                stalePortfolios = 0,
                successCount = 0,
                errorCount = 0
            )
        )
        verify(valuationService, never()).getPositions(any(), any(), any())
    }

    @Test
    fun `should value nothing when every portfolio is up to date`() {
        val fresh = portfolio("FRESH", LocalDate.now(dateUtils.zoneId))
        whenever(portfolioServiceClient.portfolios).thenReturn(PortfoliosResponse(listOf(fresh)))

        val result = controller.revalueStalePortfolios(force = false)

        assertThat(result).isEqualTo(
            ValuationAdminController.RevalueResponse(
                totalPortfolios = 1,
                stalePortfolios = 0,
                successCount = 0,
                errorCount = 0
            )
        )
        verify(valuationService, never()).getPositions(any(), any(), any())
    }

    @Test
    fun `should value only stale and never valued portfolios`() {
        val fresh = portfolio("FRESH", LocalDate.now(dateUtils.zoneId))
        val stale = portfolio("STALE", LocalDate.now(dateUtils.zoneId).minusDays(3))
        val never = portfolio("NEVER", null)
        whenever(portfolioServiceClient.portfolios).thenReturn(PortfoliosResponse(listOf(fresh, stale, never)))

        val result = controller.revalueStalePortfolios(force = false)

        assertThat(result.totalPortfolios).isEqualTo(3)
        assertThat(result.stalePortfolios).isEqualTo(2)
        assertThat(result.successCount).isEqualTo(2)
        assertThat(result.errorCount).isZero()
        verify(valuationService).getPositions(eq(stale), eq(DateUtils.TODAY), eq(true))
        verify(valuationService).getPositions(eq(never), eq(DateUtils.TODAY), eq(true))
        verify(valuationService, never()).getPositions(eq(fresh), any(), any())
    }

    @Test
    fun `should value every portfolio when forced and collect failures`() {
        val fresh = portfolio("FRESH", LocalDate.now(dateUtils.zoneId))
        val rest = portfolio("REST", null)
        val biz = portfolio("BIZ", null)
        whenever(portfolioServiceClient.portfolios).thenReturn(PortfoliosResponse(listOf(fresh, rest, biz)))
        whenever(valuationService.getPositions(any(), any(), any())).thenAnswer {
            when (it.getArgument<Portfolio>(0).code) {
                "REST" -> throw RestClientException("down")
                "BIZ" -> throw BusinessException("bad")
                else -> null
            }
        }

        val result = controller.revalueStalePortfolios(force = true)

        assertThat(result.totalPortfolios).isEqualTo(3)
        assertThat(result.stalePortfolios).isEqualTo(3)
        assertThat(result.successCount).isEqualTo(1)
        assertThat(result.errorCount).isEqualTo(2)
        assertThat(result.errors).containsExactly("REST: down", "BIZ: bad")
    }

    private fun portfolio(
        code: String,
        valuedAt: LocalDate?
    ): Portfolio =
        Portfolio(
            id = code,
            code = code,
            name = "$code Portfolio",
            currency = Currency("USD"),
            base = Currency("USD"),
            valuedAt = valuedAt
        )
}