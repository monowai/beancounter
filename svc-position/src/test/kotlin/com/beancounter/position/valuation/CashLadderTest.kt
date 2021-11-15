package com.beancounter.position.valuation

import com.beancounter.auth.server.AuthConstants
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.position.Constants.Companion.CASH
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
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
import java.math.BigDecimal

/**
 * Verify cash impact on purchase and sale. The contract data for this test
 * originates in CashTrnTests
 */
@WebAppConfiguration
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles("test")
@Tag("slow")
@SpringBootTest
internal class CashLadderTest {
    @Autowired
    private lateinit var wac: WebApplicationContext
    private lateinit var mockMvc: MockMvc
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    private val test = "CASHLADDER"

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
    fun positionRequestFromTransactions() {
        val date = "2019-10-18"
        val msft = getAsset(NASDAQ, "AAPL")
        val usdCash = getAsset(CASH, "${USD.code} ${CASH.code}")
        val nzdCash = getAsset(CASH, "${NZD.code} ${CASH.code}")
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
            .hasFieldOrPropertyWithValue("code", portfolio.code)
        assertThat(positionResponse.data.asAt).isEqualTo(date)

        assertThat(positionResponse.data.positions)
            .hasSize(3)
            .containsKeys(toKey(msft), toKey(usdCash), toKey(nzdCash))

        // Working back.  The stock purchase should debit cash
        assertThat(positionResponse.data.positions[toKey(msft)]!!.moneyValues)
            .isNotNull
        assertThat(positionResponse.data.positions[toKey(usdCash)]!!.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal("2500.0"))

        assertThat(positionResponse.data.positions[toKey(nzdCash)]!!.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal("3507.46"))

        // Cash does not track purchases and sales totals.
        assertThat(positionResponse.data.positions[toKey(nzdCash)]!!.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue("costValue", BigDecimal("3507.46")) // Purchases - Sales

        // ToDo: Figure out cash fx rates to apply at cost.
        assertThat(positionResponse.data.positions[toKey(nzdCash)]!!.moneyValues[Position.In.PORTFOLIO])
            .hasFieldOrPropertyWithValue("costValue", BigDecimal("5174.13"))
    }
}
