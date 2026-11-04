package com.beancounter.position.holdings

import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.model.AuthConstants
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.contracts.SectorExposureData
import com.beancounter.position.StubbedTest
import com.beancounter.position.allocation.SectorExposureService
import com.beancounter.position.utils.TestHelpers
import com.beancounter.position.valuation.AggregateValuationRequest
import com.beancounter.position.valuation.Valuation
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
import java.math.BigDecimal

/**
 * An M2M caller (svc-retire projecting a shared plan) owns no portfolios of its own, so
 * `/sector-exposure` must fetch each requested id through svc-data, as `/allocation` does.
 */
@StubbedTest
internal class SectorExposureMvcTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @MockitoBean
    private lateinit var valuationService: Valuation

    @MockitoBean
    private lateinit var sectorExposureService: SectorExposureService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private val sharedPortfolio = TestHelpers.createTestPortfolio("plan-owner-portfolio")

    private fun system() =
        jwt().jwt(mockAuthConfig.getUserToken()).authorities(SimpleGrantedAuthority(AuthConstants.SCOPE_SYSTEM))

    @Test
    fun `should read sector exposure of a shared portfolio for a system caller`() {
        whenever(portfolioServiceClient.portfolios).thenReturn(PortfoliosResponse(emptyList()))
        whenever(portfolioServiceClient.getPortfolioById(sharedPortfolio.id)).thenReturn(sharedPortfolio)
        val positions = TestHelpers.createTestPositions(sharedPortfolio)
        whenever(
            valuationService.getAggregatedPositions(
                AggregateValuationRequest(listOf(sharedPortfolio), "2024-01-15", value = true)
            )
        ).thenReturn(PositionResponse(positions))
        whenever(sectorExposureService.calculateSectorExposure(any(), any()))
            .thenReturn(SectorExposureData(totalValue = BigDecimal("1000"), currency = "SGD"))

        mockMvc
            .perform(
                get("/sector-exposure")
                    .param("ids", sharedPortfolio.id)
                    .param("asAt", "2024-01-15")
                    .with(system())
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.data.totalValue").value(1000))
            .andExpect(jsonPath("$.data.currency").value("SGD"))
    }
}