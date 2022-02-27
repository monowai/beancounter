package com.beancounter.position.valuation

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.KMI
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers

/**
 * Corporate actions against contracts.
 */

@WebAppConfiguration
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles("test")
@Tag("slow")
@SpringBootTest
@AutoConfigureMockAuth
@AutoConfigureMockMvc
internal class TrnValuationTest {
    private lateinit var token: Jwt

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private val objectMapper: ObjectMapper = BcJson().objectMapper

    private val test = "TEST"

    var portfolio: Portfolio = Portfolio(
        id = test,
        code = test,
        name = "${NZD.code} Portfolio",
        currency = NZD,
        base = USD,
        owner = owner
    )

    @Autowired
    fun setUser() {
        token = mockAuthConfig.getUserToken(SystemUser("test-user", "test-user@testing.com"))
    }

    @Test
    fun singleAssetPosition() {
        val dateUtils = DateUtils()
        val query = TrustedTrnQuery(
            portfolio,
            dateUtils.getDate("2020-05-01"),
            KMI
        )
        val json = mockMvc.perform(
            MockMvcRequestBuilders.post("/query")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .content(objectMapper.writeValueAsBytes(query))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn().response.contentAsString

        assertThat(json).isNotNull

        val (data) = objectMapper.readValue(json, PositionResponse::class.java)
        assertThat(data).isNotNull.hasFieldOrProperty("positions")
        assertThat(data.positions).hasSize(1)
        val position = data.positions["$KMI:NYSE"]
        assertThat(position).isNotNull
    }

    private val code = "code"

    @Test
    fun positionRequestFromTransactions() {
        val date = "2019-10-18"
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/{portfolioCode}/$date", portfolio.code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(
                MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE)
            ).andReturn()
            .response
            .contentAsString

        val positionResponse = objectMapper.readValue(json, PositionResponse::class.java)
        assertThat(positionResponse).isNotNull
        assertThat(positionResponse.data.portfolio)
            .isNotNull
            .hasFieldOrPropertyWithValue(code, portfolio.code)
        assertThat(positionResponse.data.asAt).isEqualTo(date)
        assertThat(positionResponse.data[getAsset(NASDAQ, "AAPL")])
            .isNotNull
    }

    @Test
    fun emptyPortfolioPositionsReturned() {
        val empty = "EMPTY"
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/{portfolioCode}/${DateUtils.today}", empty)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(
            MockMvcResultMatchers.status().isOk
        )
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn().response.contentAsString
        val positionResponse = objectMapper.readValue(json, PositionResponse::class.java)
        assertThat(positionResponse).isNotNull
        assertThat(positionResponse.data.portfolio)
            .isNotNull
            .hasFieldOrPropertyWithValue(code, empty)
        assertThat(positionResponse.data).isNotNull
        assertThat(positionResponse.data.positions).isEmpty()
    }
}
