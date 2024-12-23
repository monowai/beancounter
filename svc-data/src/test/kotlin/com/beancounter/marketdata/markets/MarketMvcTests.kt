package com.beancounter.marketdata.markets

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.contracts.Payload
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.marketdata.SpringMvcDbTest
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

@SpringMvcDbTest
@AutoConfigureMockAuth
internal class MarketMvcTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt

    @BeforeEach
    fun mockToken() {
        token = mockAuthConfig.login("MarketMvcTests@testing.com")
    }

    @Test
    fun `markets are retrieved for authorised user`() {
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/markets")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val marketResponse =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    MarketResponse::class.java
                )
        Assertions.assertThat(marketResponse).isNotNull.hasFieldOrProperty(Payload.DATA)
        Assertions.assertThat(marketResponse.data).isNotEmpty
    }

    @Test
    @Throws(Exception::class)
    fun is_SingleMarketFoundCaseInsensitive() {
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/markets/nzx")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val marketResponse =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    MarketResponse::class.java
                )
        Assertions.assertThat(marketResponse).isNotNull.hasFieldOrProperty(Payload.DATA)
        Assertions.assertThat(marketResponse.data).isNotNull.hasSize(1)
        val nzx = marketResponse.data!!.iterator().next()
        Assertions
            .assertThat(nzx)
            .hasNoNullFieldsOrPropertiesExcept(
                "currencyId",
                "timezoneId",
                "enricher"
            )
    }

    @Test
    @Throws(Exception::class)
    fun is_SingleMarketBadRequest() {
        val result =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get("/markets/non-existent")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().is4xxClientError)
        Assertions
            .assertThat(result.andReturn().resolvedException)
            .isNotNull
            .isInstanceOfAny(BusinessException::class.java)
    }
}