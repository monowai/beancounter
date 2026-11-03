package com.beancounter.position.holdings

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.NetWorth
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.StubbedTest
import com.beancounter.position.networth.NetWorthService
import com.beancounter.position.utils.TestHelpers
import com.beancounter.position.valuation.AggregateValuationRequest
import com.beancounter.position.valuation.Valuation
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * A home-screen widget's token carries only `beancounter:widget`. It reaches the
 * net-worth and aggregated reads (listed in `auth.widget-routes` and allowed by each
 * handler) and nothing else svc-position serves.
 */
@StubbedTest
internal class WidgetScopeMvcTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @MockitoBean
    private lateinit var valuationService: Valuation

    @MockitoBean
    private lateinit var netWorthService: NetWorthService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private val portfolio = TestHelpers.createTestPortfolio("widget-portfolio")

    private fun widget() =
        jwt().jwt(mockAuthConfig.getUserToken()).authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_WIDGET))

    @BeforeEach
    fun setUp() {
        whenever(portfolioServiceClient.getPortfolioById(portfolio.id)).thenReturn(portfolio)
    }

    @Test
    fun `should let a widget token read net worth`() {
        whenever(
            netWorthService.calculate(any(), any(), any(), any())
        ).thenReturn(NetWorth(asAt = "2024-01-15", currency = "SGD"))

        mockMvc
            .perform(get("/net-worth").param("ids", portfolio.id).param("currency", "SGD").with(widget()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.currency").value("SGD"))
    }

    @Test
    fun `should let a widget token read aggregated positions`() {
        whenever(valuationService.getAggregatedPositions(any()))
            .thenReturn(PositionResponse(TestHelpers.createTestPositions(portfolio)))

        mockMvc
            .perform(get("/aggregated").param("ids", portfolio.id).with(widget()))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.portfolio.id").value(portfolio.id))
    }

    @Test
    fun `should bind the aggregated query parameters into one valuation request`() {
        whenever(portfolioServiceClient.portfolios).thenReturn(PortfoliosResponse(listOf(portfolio)))
        whenever(valuationService.getAggregatedPositions(any()))
            .thenReturn(PositionResponse(TestHelpers.createTestPositions(portfolio)))

        mockMvc
            .perform(
                get("/aggregated")
                    .param("codes", portfolio.code)
                    .param("asAt", "2024-01-15")
                    .param("value", "false")
                    .param("currency", "sgd")
                    .param("live", "true")
                    .with(widget())
            ).andExpect(status().isOk)

        verify(valuationService).getAggregatedPositions(
            AggregateValuationRequest(
                listOf(portfolio),
                "2024-01-15",
                value = false,
                targetCurrencyCode = "sgd",
                live = true
            )
        )
    }

    @Test
    fun `should bind net worth portfolio codes`() {
        whenever(portfolioServiceClient.portfolios).thenReturn(PortfoliosResponse(listOf(portfolio)))
        whenever(netWorthService.calculate(any(), any(), any(), any()))
            .thenReturn(NetWorth(asAt = "2024-01-15", currency = "SGD"))

        mockMvc
            .perform(get("/net-worth").param("codes", portfolio.code).param("currency", "SGD").with(widget()))
            .andExpect(status().isOk)

        verify(netWorthService).calculate(eq(listOf(portfolio)), eq(DateUtils.TODAY), eq("SGD"), eq(false))
    }

    @Test
    fun `should refuse a widget token any other position read`() {
        mockMvc.perform(get("/id/{id}", portfolio.id).with(widget())).andExpect(status().isForbidden)
        mockMvc.perform(get("/allocation").with(widget())).andExpect(status().isForbidden)
    }
}