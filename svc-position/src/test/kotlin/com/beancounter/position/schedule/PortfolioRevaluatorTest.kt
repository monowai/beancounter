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
import org.assertj.core.api.Assertions.assertThat
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

@ExtendWith(MockitoExtension::class)
class PortfolioRevaluatorTest {
    @Mock
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Mock
    private lateinit var valuationService: Valuation

    @Mock
    private lateinit var loginService: LoginService

    @Mock
    private lateinit var authConfig: AuthConfig

    private lateinit var revaluator: PortfolioRevaluator

    @BeforeEach
    fun setUp() {
        revaluator = PortfolioRevaluator(portfolioServiceClient, valuationService)
        revaluator.setLoginService(loginService)
    }

    @Test
    fun `revalueAll values every portfolio`() {
        val portfolios = listOf(portfolio("PORT1"), portfolio("PORT2"), portfolio("PORT3"))
        wireLogin(portfolios)

        val result = revaluator.revalueAll(reason = "test")

        verify(valuationService).getPositions(eq(portfolios[0]), eq(DateUtils.TODAY), eq(true))
        verify(valuationService).getPositions(eq(portfolios[1]), eq(DateUtils.TODAY), eq(true))
        verify(valuationService).getPositions(eq(portfolios[2]), eq(DateUtils.TODAY), eq(true))
        verify(valuationService, times(3)).getPositions(any(), any(), any())
        assertThat(result.status).isEqualTo(PortfolioRevaluator.ResultStatus.COMPLETED)
        assertThat(result.successCount).isEqualTo(3)
        assertThat(result.errorCount).isZero()
    }

    @Test
    fun `revalueAll handles empty portfolio list`() {
        wireLogin(emptyList())

        val result = revaluator.revalueAll(reason = "test")

        verify(valuationService, never()).getPositions(any(), any(), any())
        assertThat(result.status).isEqualTo(PortfolioRevaluator.ResultStatus.COMPLETED)
    }

    @Test
    fun `revalueAll returns SKIPPED_NO_LOGIN when LoginService missing`() {
        val noLogin = PortfolioRevaluator(portfolioServiceClient, valuationService)
        // intentionally not calling setLoginService

        val result = noLogin.revalueAll(reason = "test")

        verify(portfolioServiceClient, never()).getAllPortfolios(any())
        assertThat(result.status).isEqualTo(PortfolioRevaluator.ResultStatus.SKIPPED_NO_LOGIN)
    }

    private fun portfolio(code: String): Portfolio =
        Portfolio(
            id = code,
            code = code,
            name = "$code Portfolio",
            currency = Currency("USD"),
            base = Currency("USD")
        )

    private fun wireLogin(portfolios: List<Portfolio>) {
        val token =
            OpenIdResponse(
                token = "test-token",
                scope = "openid",
                expiry = 3600,
                type = "Bearer"
            )
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