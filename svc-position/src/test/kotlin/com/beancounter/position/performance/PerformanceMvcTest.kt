package com.beancounter.position.performance

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.exception.BusinessException
import com.beancounter.position.StubbedTest
import com.beancounter.position.utils.TestHelpers
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
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
 * A performance pass that cannot value a holding, such as one missing an FX rate,
 * fails the request with a 400 that names the problem rather than a wrong figure.
 */
@StubbedTest
internal class PerformanceMvcTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @MockitoBean
    private lateinit var performanceService: PerformanceService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private val portfolio = TestHelpers.createTestPortfolio("performance-portfolio")

    @Test
    fun `should answer 400 naming the pair when an FX rate is missing`() {
        whenever(portfolioServiceClient.getPortfolioByCode(portfolio.code)).thenReturn(portfolio)
        whenever(performanceService.calculate(any(), any()))
            .thenThrow(BusinessException("No FX rate for SGD:USD as at 2024-06-03"))

        mockMvc
            .perform(
                get("/{code}/performance", portfolio.code).with(
                    jwt()
                        .jwt(
                            mockAuthConfig.getUserToken()
                        ).authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_USER))
                )
            ).andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail", containsString("SGD:USD")))
    }
}