package com.beancounter.position.valuation

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.Position
import com.beancounter.common.utils.AssetKeyUtils.Companion.toKey
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.position.Constants.Companion.CASH
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import com.beancounter.position.StubbedTest
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

private const val PROP_COST_VALUE = "costValue"

/**
 * Verify cash impact on purchase and sale. The contract data for this test
 * originates in CashTrnTests
 */
@StubbedTest
internal class CashLadderMvcTests {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private val objectMapper: ObjectMapper = BcJson().objectMapper

    private val test = "CASHLADDER"

    var portfolio: Portfolio =
        Portfolio(
            id = test,
            code = test,
            name = "${NZD.code} Portfolio",
            currency = NZD,
            base = USD,
            owner = owner,
        )

    @Test
    fun positionRequestFromTransactions() {
        val date = "2021-10-18"
        val apple = getTestAsset(code = "AAPL", market = NASDAQ)

        val usdCash = getTestAsset(code = USD.code, market = CASH)
        val nzdCash = getTestAsset(code = NZD.code, market = CASH)
        val json =
            mockMvc.perform(
                MockMvcRequestBuilders.get("/{portfolioCode}/$date", portfolio.code)
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(mockAuthConfig.getUserToken()))
                    .contentType(MediaType.APPLICATION_JSON_VALUE),
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(
                    MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE),
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
            .containsKeys(toKey(apple), toKey(usdCash), toKey(nzdCash))

        // Working back.  The stock purchase should debit cash
        assertThat(positionResponse.data.positions[toKey(apple)]!!.moneyValues)
            .isNotNull
        assertThat(positionResponse.data.positions[toKey(usdCash)]!!.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal("2500.0"))

        val cashResult = "2500.00"
        assertThat(positionResponse.data.positions[toKey(usdCash)]!!.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue("marketValue", BigDecimal(cashResult))
            .hasFieldOrPropertyWithValue(PROP_COST_VALUE, BigDecimal(cashResult))

        assertThat(positionResponse.data.positions[toKey(nzdCash)]!!.quantityValues)
            .hasFieldOrPropertyWithValue("total", BigDecimal("3507.46"))

        // Cash does not track purchases and sales totals.
        assertThat(positionResponse.data.positions[toKey(nzdCash)]!!.moneyValues[Position.In.TRADE])
            .hasFieldOrPropertyWithValue(PROP_COST_VALUE, BigDecimal("3507.46")) // Purchases - Sales
    }
}
