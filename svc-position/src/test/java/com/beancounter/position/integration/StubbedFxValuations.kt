package com.beancounter.position.integration

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.client.AssetService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.*
import com.beancounter.common.utils.BcJson
import com.beancounter.position.service.Accumulator
import com.beancounter.position.service.Valuation
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.*

@WebAppConfiguration
@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.LOCAL, ids = ["org.beancounter:svc-data:+:stubs:10999"])
@ActiveProfiles("test")
@Tag("slow")
@SpringBootTest
internal class StubbedFxValuations {
    private val authorityRoleConverter = AuthorityRoleConverter()
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var context: WebApplicationContext

    @Autowired
    private lateinit var accumulator: Accumulator

    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var valuation: Valuation

    @Autowired
    private lateinit var staticService: StaticService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var portfolioService: PortfolioServiceClient
    private var token: Jwt? = null

    @Autowired
    fun mockServices() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        // Setup a user account
        val user = SystemUser("user", "user@testing.com")
        token = TokenUtils.getUserToken(user)
    }

    private fun getPositions(asset: Asset): Positions {
        val trn = Trn(TrnType.BUY, asset, BigDecimal(100))
        trn.tradeAmount = BigDecimal(2000)
        val portfolio = portfolioService.getPortfolioByCode("TEST")
        val positions = Positions(portfolio)
        positions.asAt = "2019-10-18"
        positions.add(accumulator.accumulate(trn, portfolio, Position(asset)))
        return positions
    }

    private fun getValuedPositions(asset: Asset): Positions {
        val positions = getPositions(asset)
        assertThat(positions).isNotNull
        assertThat(valuation).isNotNull
        valuation.value(positions)
        return positions
    }

    @Test
    @Throws(Exception::class)
    fun is_MvcValuingPositions() {
        val asset = ebay
        assertThat(asset).hasFieldOrProperty("name")
        val positions = getPositions(asset)
        val positionResponse = PositionResponse(positions)
        assertThat(mockMvc).isNotNull
        val json = mockMvc.perform(MockMvcRequestBuilders.post("/value")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(objectMapper.writeValueAsString(positionResponse))
        ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn().response.contentAsString
        val fromJson = objectMapper.readValue(json, PositionResponse::class.java)
        assertThat(fromJson).isNotNull.hasFieldOrProperty("data")
        val jsonPositions = fromJson.data
        assertThat(jsonPositions).isNotNull
        assertThat(jsonPositions.positions).hasSize(positions.positions.size)
        assertThat(jsonPositions.totals).hasSize(1)
        var position: Position? = null
        for (key in jsonPositions.positions.keys) {
            position = jsonPositions.positions[key]
            assertThat(position!!.asset)
                    .hasFieldOrPropertyWithValue("name", asset.name)
        }
        assertThat(position).isNotNull
        val totalKey = jsonPositions.totals.keys.iterator().next()
        val moneyValues = position!!.moneyValues[totalKey]
        assertThat(moneyValues!!.weight!!.compareTo(BigDecimal.ONE)).isEqualTo(0)
        val moneyTotal = jsonPositions.totals[totalKey]
        assertThat(moneyTotal)
                .hasFieldOrPropertyWithValue("total", moneyValues.marketValue)
    }

    private val ebay: Asset
        get() {
            val assetInputMap: MutableMap<String, AssetInput> = HashMap()
            assetInputMap["EBAY:NASDAQ"] = AssetInput("NASDAQ", "EBAY")
            val assetRequest = AssetRequest(assetInputMap)
            val assetResponse = assetService.process(assetRequest)
            assertThat(assetResponse!!.data).hasSize(1)
            return assetResponse.data["EBAY:NASDAQ"] ?: error("EBAY Not Found. This should never happen")
        }

    @Test
    @Throws(Exception::class)
    fun is_MvcRestException() {
        val result = mockMvc.perform(MockMvcRequestBuilders.post("/value")
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString("{asdf}"))
        ).andExpect(MockMvcResultMatchers.status().is4xxClientError).andReturn()
        val someException = Optional.ofNullable(result.resolvedException as HttpMessageNotReadableException)
        assertThat(someException.isPresent).isTrue()
    }

    @Test
    fun is_MarketValuationCalculatedAsAt() {
        val asset = ebay

        // We need to have a Quantity in order to get the price, so create a position
        val positions = getValuedPositions(asset)
        assertThat(positions[asset].getMoneyValues(Position.In.TRADE, asset.market.currency))
                .hasFieldOrPropertyWithValue("unrealisedGain", BigDecimal("8000.00"))
                .hasFieldOrPropertyWithValue("priceData.close", BigDecimal("100.00"))
                .hasFieldOrPropertyWithValue("marketValue", BigDecimal("10000.00"))
                .hasFieldOrPropertyWithValue("totalGain", BigDecimal("8000.00"))
    }

    @Test
    fun is_AssetAndCurrencyHydratedFromValuationRequest() {
        val asset = ebay
        val positions = getValuedPositions(asset)
        val position = positions[asset]
        assertThat(position)
                .hasFieldOrProperty("asset")
        assertThat(position.asset.market)
                .hasNoNullFieldsOrPropertiesExcept("currencyId", "timezoneId", "enricher")
        assertThat(position.moneyValues[Position.In.PORTFOLIO]!!.currency)
                .hasNoNullFieldsOrProperties()
        assertThat(position.moneyValues[Position.In.BASE]!!.currency)
                .hasNoNullFieldsOrProperties()
        assertThat(position.moneyValues[Position.In.TRADE]!!.currency)
                .hasNoNullFieldsOrProperties()
    }
}