package com.beancounter.marketdata.assets

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.model.AssetFundamentals
import com.beancounter.marketdata.SpringMvcDbTest
import com.beancounter.marketdata.classification.AssetFundamentalsRepository
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.LocalDate

/**
 * `GET /assets/{assetId}/fundamentals` returns the persisted SEC snapshot, 404 when none exists.
 */
@SpringMvcDbTest
internal class AssetFundamentalsControllerTest {
    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var fundamentalsRepository: AssetFundamentalsRepository

    @Test
    fun `should return the fundamentals snapshot for an asset that has one`() {
        fundamentalsRepository.save(
            AssetFundamentals(
                assetId = "fund-asset-1",
                fiscalYearEnd = LocalDate.of(2025, 9, 27),
                fiscalYear = 2025,
                epsDiluted = BigDecimal("7.46"),
                revenue = BigDecimal("416161000000"),
                netIncome = null,
                dividendsPerShare = BigDecimal("1.02"),
                sharesOutstanding = 14773123000L,
                asOf = LocalDate.of(2026, 10, 7)
            )
        )

        mockMvc
            .perform(
                get("/assets/{assetId}/fundamentals", "fund-asset-1")
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.assetId").value("fund-asset-1"))
            .andExpect(jsonPath("$.source").value("SEC"))
            .andExpect(jsonPath("$.fiscalYear").value(2025))
            .andExpect(jsonPath("$.epsDiluted").value(7.46))
            .andExpect(jsonPath("$.sharesOutstanding").value(14773123000L))
    }

    @Test
    fun `should return 404 when the asset has no fundamentals`() {
        mockMvc
            .perform(
                get("/assets/{assetId}/fundamentals", "no-such-asset")
                    .with(jwt().jwt(mockAuthConfig.getUserToken()))
            ).andExpect(status().isNotFound)
    }
}