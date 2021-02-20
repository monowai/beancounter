package com.beancounter.position.integration

import com.beancounter.auth.server.RoleHelper
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@WebAppConfiguration
@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.LOCAL, ids = ["org.beancounter:svc-data:+:stubs:10999"])
@ActiveProfiles("test")
@Tag("slow")
@SpringBootTest
internal class StubbedTrnValuations {
    @Autowired
    private lateinit var wac: WebApplicationContext
    private lateinit var mockMvc: MockMvc
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    @Test
    @WithMockUser(username = "test-user", roles = [RoleHelper.OAUTH_USER])
    fun is_SingleAssetPosition() {
        val dateUtils = DateUtils()
        val portfolio = Portfolio(
            "TEST",
            "TEST",
            "NZD Portfolio",
            Currency("NZD"),
            Currency("USD"),
            null
        )
        val query = TrustedTrnQuery(
            portfolio,
            dateUtils.getDate("2020-05-01")!!,
            "KMI"
        )
        val json = mockMvc.perform(
            MockMvcRequestBuilders.post("/query")
                .content(objectMapper.writeValueAsBytes(query))
                .contentType(MediaType.APPLICATION_JSON)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn().response.contentAsString
        assertThat(json).isNotNull()
        val (data) = objectMapper.readValue(json, PositionResponse::class.java)
        assertThat(data).isNotNull.hasFieldOrProperty("positions")
        assertThat(data.positions).hasSize(1)
        val position = data.positions["KMI:NYSE"]
        assertThat(position).isNotNull
    }

    @Test
    @WithMockUser(username = "test-user", roles = [RoleHelper.OAUTH_USER])
    fun is_PositionRequestFromTransactions() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/{portfolioCode}/2019-10-18", "TEST")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
        ).andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andReturn().response.contentAsString
        val positionResponse = objectMapper.readValue(json, PositionResponse::class.java)
        assertThat(positionResponse).isNotNull
        assertThat(positionResponse.data.portfolio)
            .isNotNull
            .hasFieldOrPropertyWithValue("code", "TEST")
        assertThat(positionResponse.data.asAt).isEqualTo("2019-10-18")
        assertThat(positionResponse.data[getAsset("NASDAQ", "AAPL")])
            .isNotNull
    }

    @Test
    @WithMockUser(username = "test-user", roles = [RoleHelper.OAUTH_USER])
    fun is_EmptyPortfolioPositionsReturned() {
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/{portfolioCode}/today", "EMPTY")
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
            .hasFieldOrPropertyWithValue("code", "EMPTY")
        assertThat(positionResponse.data).isNotNull
        assertThat(positionResponse.data.positions).isNull()
    }
}
