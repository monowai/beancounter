package com.beancounter.marketdata.utils

import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.input.TrnInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import org.assertj.core.api.Assertions
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * Helper for MVC tests.
 */
class BcMvcHelper(
    private val mockMvc: MockMvc,
    val token: Jwt
) {
    companion object {
        const val TRADE_DATE = "2018-01-01"
        const val TRNS_ROOT = "/trns"
        const val ASSET_ROOT = "/assets"
        const val PORTFOLIO_ROOT = "/portfolios"

        @JvmStatic
        fun portfolios(
            mockMvc: MockMvc,
            token: Jwt
        ) = objectMapper
            .readValue(
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get(PORTFOLIO_ROOT)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn()
                    .response.contentAsString,
                PortfoliosResponse::class.java
            ).data

        @JvmStatic
        fun portfolioById(
            portfolioId: String,
            mockMvc: MockMvc,
            token: Jwt
        ) = objectMapper
            .readValue(
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .get("$PORTFOLIO_ROOT/$portfolioId")
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn()
                    .response.contentAsString,
                PortfolioResponse::class.java
            ).data

        @JvmStatic
        fun portfolioCreate(
            portfolioInput: Collection<PortfolioInput>,
            mockMvc: MockMvc,
            token: Jwt
        ): PortfoliosResponse =
            objectMapper.readValue(
                mockMvc
                    .perform(
                        MockMvcRequestBuilders
                            .post(PORTFOLIO_ROOT)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                            .content(objectMapper.writeValueAsBytes(PortfoliosRequest(portfolioInput)))
                            .contentType(MediaType.APPLICATION_JSON)
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn()
                    .response.contentAsString,
                PortfoliosResponse::class.java
            )

        @JvmStatic
        fun portfolioCreate(
            portfolioInput: PortfolioInput,
            mockMvc: MockMvc,
            token: Jwt
        ) = portfolioCreate(setOf(portfolioInput), mockMvc, token)

        @JvmStatic
        fun portfolioByCode(
            code: String,
            mockMvc: MockMvc,
            token: Jwt
        ) = objectMapper.readValue(
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("$PORTFOLIO_ROOT/code/$code")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .response.contentAsString,
            PortfolioResponse::class.java
        )
    }

    fun asset(assetRequest: AssetRequest): Asset {
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post(ASSET_ROOT)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .content(objectMapper.writeValueAsBytes(assetRequest))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper.readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        Assertions.assertThat(data.values).isNotNull
        return data.values.iterator().next()
    }

    fun getTrnById(trnId: String): MvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .get("$TRNS_ROOT/$trnId")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    fun postTrn(trnRequest: TrnRequest): MvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post(TRNS_ROOT)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .content(objectMapper.writeValueAsBytes(trnRequest))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    fun patchTrn(
        portfolioId: String,
        trnId: String,
        trnInput: TrnInput
    ): MvcResult =
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .patch("$TRNS_ROOT/$portfolioId/$trnId")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .content(objectMapper.writeValueAsBytes(trnInput))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()

    fun portfolio(portfolio: PortfolioInput): Portfolio {
        val portfolioResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post(PORTFOLIO_ROOT)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .content(objectMapper.writeValueAsBytes(PortfoliosRequest(setOf(portfolio))))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper.readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)
        return data.iterator().next()
    }

    fun registerUser() {
        mockMvc
            .perform(
                MockMvcRequestBuilders
                    .post("/register")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .with(SecurityMockMvcRequestPostProcessors.csrf())
                    .content(objectMapper.writeValueAsBytes(RegistrationRequest()))
                    .contentType(MediaType.APPLICATION_JSON)
            ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
    }
}