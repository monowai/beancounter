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
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * MVC helper to post secured transactions to Web endpoints.
 *
 */
class BcMvcHelper(val mockMvc: MockMvc, val token: Jwt) {
    // Test Constants
    companion object {
        const val tradeDate = "2018-01-01"

        // Test Constants
        const val trnsRoot = "/trns"
        const val uriTrnForPortfolio = "$trnsRoot/portfolio/{portfolioId}/{asAt}"
        const val assetRoot = "/assets"
        const val portfolioRoot = "/portfolios"
        const val portfolioByCode = "$portfolioRoot/code/{code}"
        const val portfolioById = "$portfolioRoot/{id}"

        @JvmStatic
        fun portfolios(mockMvc: MockMvc, token: Jwt) = objectMapper
            .readValue(
                mockMvc.perform(
                    MockMvcRequestBuilders.get(portfolioRoot)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn().response.contentAsString,
                PortfoliosResponse::class.java,
            ).data

        @JvmStatic
        fun portfolioById(portfolioId: String, mockMvc: MockMvc, token: Jwt) = objectMapper
            .readValue(
                mockMvc.perform(
                    MockMvcRequestBuilders.get(portfolioById, portfolioId)
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token)),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                    .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                    .andReturn().response.contentAsString,
                PortfolioResponse::class.java,
            )
            .data

        @JvmStatic
        fun portfolioCreate(
            portfolioInput: Collection<PortfolioInput>,
            mockMvc: MockMvc,
            token: Jwt,
        ): PortfoliosResponse =
            objectMapper
                .readValue(
                    mockMvc.perform(
                        MockMvcRequestBuilders.post(portfolioRoot)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                            .content(
                                objectMapper
                                    .writeValueAsBytes(PortfoliosRequest(portfolioInput)),
                            )
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(MockMvcResultMatchers.status().isOk)
                        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                        .andReturn().response.contentAsString,
                    PortfoliosResponse::class.java,
                )

        @JvmStatic
        fun portfolioCreate(portfolioInput: PortfolioInput, mockMvc: MockMvc, token: Jwt): PortfoliosResponse =
            portfolioCreate(setOf(portfolioInput), mockMvc, token)

        @JvmStatic
        fun portfolioByCode(code: String, mockMvc: MockMvc, token: Jwt): PortfolioResponse =
            objectMapper
                .readValue(
                    mockMvc.perform(
                        MockMvcRequestBuilders.get(portfolioByCode, code)
                            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andExpect(
                        MockMvcResultMatchers.status()
                            .isOk,
                    ).andExpect(
                        MockMvcResultMatchers.content()
                            .contentType(MediaType.APPLICATION_JSON),
                    ).andReturn()
                        .response.contentAsString,
                    PortfolioResponse::class.java,
                )
    }

    private val objectMapper = BcJson().objectMapper

    fun asset(assetRequest: AssetRequest): Asset {
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post(assetRoot)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .content(objectMapper.writeValueAsBytes(assetRequest))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        Assertions.assertThat(data.values).isNotNull
        return data.values.iterator().next()
    }

    fun getTrnById(portfolioId: String, trnId: String): MvcResult = mockMvc.perform(
        MockMvcRequestBuilders.get("$trnsRoot/$portfolioId/$trnId")
            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andReturn()

    fun postTrn(trnRequest: TrnRequest): MvcResult = mockMvc.perform(
        MockMvcRequestBuilders.post(trnsRoot)
            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            .content(objectMapper.writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andReturn()

    fun patchTrn(portfolioId: String, trnId: String, trnInput: TrnInput): MvcResult = mockMvc.perform(
        MockMvcRequestBuilders.patch("$trnsRoot/$portfolioId/$trnId")
            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
            .content(objectMapper.writeValueAsBytes(trnInput))
            .contentType(MediaType.APPLICATION_JSON),
    ).andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andReturn()

    fun portfolio(portfolio: PortfolioInput): Portfolio {
        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post(portfolioRoot, portfolio.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .content(ObjectMapper().writeValueAsBytes(PortfoliosRequest(setOf(portfolio))))
                .contentType(MediaType.APPLICATION_JSON),
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(portfolioResult.response.contentAsString, PortfoliosResponse::class.java)
        return data.iterator().next()
    }

    fun registerUser() {
        mockMvc.perform(
            MockMvcRequestBuilders.post("/register")
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token),
                )
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .content(
                    RegistrationUtils.objectMapper
                        .writeValueAsBytes(RegistrationRequest()),
                )
                .contentType(MediaType.APPLICATION_JSON),
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
    }
}
