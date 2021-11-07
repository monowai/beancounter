package com.beancounter.marketdata.utils

import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.RegistrationRequest
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson
import com.beancounter.marketdata.Constants
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
 * MVC helper to post secured transactions to Web endpoing.
 *
 * @Autowired
 * private lateinit var marketService: MarketService
 * @Autowired
 * private lateinit var enrichmentFactory: EnrichmentFactory
 * @MockBean
 * private lateinit var figiProxy: FigiProxy
 * ... and register the enricher
 * enrichmentFactory.register(MockEnricher())
 */
class BcMvcHelper(val mockMvc: MockMvc, val token: Jwt) {
    // Test Constants
    companion object {
        // Test Constants
        const val trnsRoot = "/trns"
        const val uriTrnForPortfolio = "$trnsRoot/portfolio/{portfolioId}"
        const val tradeDate = "2018-01-01"
    }

    private val authorityRoleConverter = AuthorityRoleConverter()
    private val objectMapper = BcJson().objectMapper

    @Throws(Exception::class)
    fun asset(assetRequest: AssetRequest): Asset {
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/assets/")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(objectMapper.writeValueAsBytes(assetRequest))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetUpdateResponse::class.java)
        Assertions.assertThat(data.values).isNotNull
        return data.values.iterator().next()
    }

    fun postTrn(trnRequest: TrnRequest): MvcResult = mockMvc.perform(
        MockMvcRequestBuilders.post(trnsRoot)
            .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
            .content(objectMapper.writeValueAsBytes(trnRequest))
            .contentType(MediaType.APPLICATION_JSON)
    ).andExpect(MockMvcResultMatchers.status().isOk)
        .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
        .andReturn()

    @Throws(Exception::class)
    fun portfolio(portfolio: PortfolioInput): Portfolio {
        val createRequest = PortfoliosRequest(setOf(portfolio))
        val portfolioResult = mockMvc.perform(
            MockMvcRequestBuilders.post("/portfolios", portfolio.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .content(ObjectMapper().writeValueAsBytes(createRequest))
                .contentType(MediaType.APPLICATION_JSON)
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
                    SecurityMockMvcRequestPostProcessors.jwt().jwt(token)
                        .authorities(RegistrationUtils.authorityRoleConverter)
                )
                .with(SecurityMockMvcRequestPostProcessors.csrf())
                .content(
                    RegistrationUtils.objectMapper
                        .writeValueAsBytes(RegistrationRequest(Constants.systemUser.email))
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
    }
}
