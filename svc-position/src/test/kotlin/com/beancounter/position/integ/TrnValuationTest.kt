package com.beancounter.position.integ

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.KMI
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.StubbedTest
import org.assertj.core.api.Assertions.assertThat
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

/**
 * Corporate actions against contracts.
 */

@StubbedTest
internal class TrnValuationTest {
    private lateinit var token: Jwt

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private val test = Constants.TEST

    var portfolio: Portfolio =
        Portfolio(
            id = test,
            code = test,
            name = "${NZD.code} Portfolio",
            currency = NZD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun configure() {
        token =
            mockAuthConfig.getUserToken(
                SystemUser(
                    "test-user",
                    "test-user@testing.com"
                )
            )
    }

    @Test
    fun singleAssetPosition() {
        val dateUtils = DateUtils()
        val query =
            TrustedTrnQuery(
                portfolio,
                dateUtils.getFormattedDate("2020-05-01"),
                KMI
            )
        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .post("/query")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .content(objectMapper.writeValueAsBytes(query))
                        .contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(
                    MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andReturn()
                .response.contentAsString

        assertThat(json).isNotNull

        val (data) =
            objectMapper.readValue(
                json,
                PositionResponse::class.java
            )
        assertThat(data).isNotNull.hasFieldOrProperty("positions")
        assertThat(data.positions).hasSize(1)
        val position = data.positions["$KMI:NYSE"]
        assertThat(position).isNotNull
    }

    private val code = "code"

    @Test
    fun positionRequestFromTransactions() {
        val date = "2019-10-18"
        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "/{portfolioCode}/$date",
                            portfolio.code
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(
                    MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andReturn()
                .response
                .contentAsString

        val positionResponse =
            objectMapper.readValue(
                json,
                PositionResponse::class.java
            )
        assertThat(positionResponse).isNotNull
        assertThat(positionResponse.data.portfolio)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                code,
                portfolio.code
            )
        assertThat(positionResponse.data.asAt).isEqualTo(date)
        assertThat(
            positionResponse.data.getOrCreate(
                getTestAsset(
                    NASDAQ,
                    "AAPL"
                )
            )
        ).isNotNull
    }

    @Test
    fun emptyPortfolioPositionsReturned() {
        val empty = "EMPTY"
        val json =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            "/{portfolioCode}/${DateUtils.TODAY}",
                            empty
                        ).with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                        .contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andExpect(
                    MockMvcResultMatchers.status().isOk
                ).andExpect(
                    MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
                ).andReturn()
                .response.contentAsString
        val positionResponse =
            objectMapper.readValue(
                json,
                PositionResponse::class.java
            )
        assertThat(positionResponse).isNotNull
        assertThat(positionResponse.data.portfolio)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                code,
                empty
            )
        assertThat(positionResponse.data).isNotNull
        assertThat(positionResponse.data.positions).isEmpty()
    }
}