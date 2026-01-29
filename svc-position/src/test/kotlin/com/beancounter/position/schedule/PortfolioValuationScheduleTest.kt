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
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

/**
 * Tests for PortfolioValuationSchedule to verify all portfolios are valued.
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
                "0 0 7 * * *"
            )
        schedule.setLoginService(loginService)
    }

    @Test
    fun `should value all portfolios`() {
        // Given - multiple portfolios
        val portfolio1 = createPortfolio("PORT1")
        val portfolio2 = createPortfolio("PORT2")
        val portfolio3 = createPortfolio("PORT3")
        setupMocks(listOf(portfolio1, portfolio2, portfolio3))

        // When
        schedule.valuePortfolios()

        // Then - should value all portfolios
        verify(valuationService).getPositions(eq(portfolio1), eq(DateUtils.TODAY), eq(true))
        verify(valuationService).getPositions(eq(portfolio2), eq(DateUtils.TODAY), eq(true))
        verify(valuationService).getPositions(eq(portfolio3), eq(DateUtils.TODAY), eq(true))
        verify(valuationService, times(3)).getPositions(any(), any(), any())
    }

    @Test
    fun `should handle empty portfolio list`() {
        // Given - no portfolios
        setupMocks(emptyList())

        // When
        schedule.valuePortfolios()

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
                "0 0 7 * * *"
            )
        // Don't set login service

        // When
        scheduleNoLogin.valuePortfolios()

        // Then - should not attempt to get portfolios
        verify(portfolioServiceClient, never()).getAllPortfolios(any())
    }

    private fun createPortfolio(code: String): Portfolio =
        Portfolio(
            id = code,
            code = code,
            name = "$code Portfolio",
            currency = Currency("USD"),
            base = Currency("USD")
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