package com.beancounter.position.schedule

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.client.LoginService
import com.beancounter.auth.model.OpenIdResponse
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.valuation.Valuation
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
import java.time.LocalDate

/**
 * Tests for PortfolioValuationSchedule to verify stale portfolio filtering.
 */
@ExtendWith(MockitoExtension::class)
class PortfolioValuationScheduleTest {
    @Mock
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Mock
    private lateinit var valuationService: Valuation

    @Mock
    private lateinit var loginService: LoginService

    @Mock
    private lateinit var authConfig: AuthConfig

    private lateinit var dateUtils: DateUtils
    private lateinit var schedule: PortfolioValuationSchedule

    @BeforeEach
    fun setUp() {
        dateUtils = DateUtils()
        schedule =
            PortfolioValuationSchedule(
                portfolioServiceClient,
                valuationService,
                dateUtils,
                "0 0 7 * * *",
                24 // stale hours
            )
        schedule.setLoginService(loginService)
    }

    @Test
    fun `should value portfolio that has never been valued`() {
        // Given - portfolio with null valuedAt
        val portfolio = createPortfolio("PORT1", null)
        setupMocks(listOf(portfolio))

        // When
        schedule.valueStalePortfolios()

        // Then - should value the portfolio
        verify(valuationService).getPositions(eq(portfolio), eq(DateUtils.TODAY), eq(true))
    }

    @Test
    fun `should value portfolio valued more than 24 hours ago`() {
        // Given - portfolio valued 2 days ago
        val portfolio = createPortfolio("PORT1", LocalDate.now().minusDays(2))
        setupMocks(listOf(portfolio))

        // When
        schedule.valueStalePortfolios()

        // Then - should value the portfolio
        verify(valuationService).getPositions(eq(portfolio), eq(DateUtils.TODAY), eq(true))
    }

    @Test
    fun `should skip portfolio valued today`() {
        // Given - portfolio valued today
        val portfolio = createPortfolio("PORT1", LocalDate.now())
        setupMocks(listOf(portfolio))

        // When
        schedule.valueStalePortfolios()

        // Then - should not value the portfolio
        verify(valuationService, never()).getPositions(any(), any(), any())
    }

    @Test
    fun `should only value stale portfolios when mix of fresh and stale`() {
        // Given - one fresh, one stale
        val freshPortfolio = createPortfolio("FRESH", LocalDate.now())
        val stalePortfolio = createPortfolio("STALE", LocalDate.now().minusDays(2))
        setupMocks(listOf(freshPortfolio, stalePortfolio))

        // When
        schedule.valueStalePortfolios()

        // Then - should only value the stale portfolio
        verify(valuationService).getPositions(eq(stalePortfolio), eq(DateUtils.TODAY), eq(true))
        verify(valuationService, never()).getPositions(eq(freshPortfolio), any(), any())
    }

    @Test
    fun `should handle empty portfolio list`() {
        // Given - no portfolios
        setupMocks(emptyList())

        // When
        schedule.valueStalePortfolios()

        // Then - should not call valuationService
        verify(valuationService, never()).getPositions(any(), any(), any())
    }

    @Test
    fun `should skip when login service not available`() {
        // Given - no login service
        val scheduleNoLogin =
            PortfolioValuationSchedule(
                portfolioServiceClient,
                valuationService,
                dateUtils,
                "0 0 7 * * *",
                24
            )
        // Don't set login service

        // When
        scheduleNoLogin.valueStalePortfolios()

        // Then - should not attempt to get portfolios
        verify(portfolioServiceClient, never()).getAllPortfolios(any())
    }

    private fun createPortfolio(
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

    private fun setupMocks(portfolios: List<Portfolio>) {
        val token =
            OpenIdResponse(
                token = "test-token",
                scope = "openid",
                expiry = 3600,
                type = "Bearer"
            )
        // Mock authConfig to provide default parameter for loginM2m()
        whenever(loginService.authConfig).thenReturn(authConfig)
        whenever(authConfig.clientSecret).thenReturn("test-secret")
        whenever(loginService.loginM2m(any())).thenReturn(token)
        whenever(loginService.retryOnJwtExpiry<Unit>(any())).thenAnswer { invocation ->
            val block = invocation.getArgument<() -> Unit>(0)
            block()
        }
        whenever(portfolioServiceClient.getAllPortfolios(any()))
            .thenReturn(PortfoliosResponse(portfolios))
    }
}