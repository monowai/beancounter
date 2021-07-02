package com.beancounter.position.integration

import com.beancounter.auth.server.AuthConstants
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
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles("test")
@Tag("slow")
@SpringBootTest
/**
 * Corporate actions against contracts.
 */
internal class StubbedTrnValuations {
    @Autowired
    private lateinit var wac: WebApplicationContext
    private lateinit var mockMvc: MockMvc
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    private val id = "blah@blah.com"
    private val owner = SystemUser(
        id = id,
        email = id,
        true,
        DateUtils().getDate("2020-06-03")
    )

    private val test = "TEST"

    var portfolio: Portfolio = Portfolio(
        id = test,
        code = test,
        name = "${NZD.code} Portfolio",
        currency = NZD,
        base = USD,
        owner = owner
    )

    @BeforeEach
    fun setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac).build()
    }

    @Test
    @WithMockUser(username = "test-user", roles = [AuthConstants.OAUTH_USER])
    fun singleAssetPosition() {
        val dateUtils = DateUtils()
        val query = TrustedTrnQuery(
            portfolio,
            dateUtils.getDate("2020-05-01"),
            KMI
        )
        val json = mockMvc.perform(
            MockMvcRequestBuilders.post("/query")
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
    @WithMockUser(username = "test-user", roles = [AuthConstants.OAUTH_USER])
    fun positionRequestFromTransactions() {
        val date = "2019-10-18"
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/{portfolioCode}/$date", portfolio.code)
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
    @WithMockUser(username = "test-user", roles = [AuthConstants.OAUTH_USER])
    fun emptyPortfolioPositionsReturned() {
        val empty = "EMPTY"
        val json = mockMvc.perform(
            MockMvcRequestBuilders.get("/{portfolioCode}/${DateUtils.today}", empty)
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
