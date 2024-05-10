package com.beancounter.position.valuation

import com.beancounter.auth.AuthConfig
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenUtils
import com.beancounter.client.AssetService
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.StaticService
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Position
import com.beancounter.common.model.Positions
import com.beancounter.common.model.SystemUser
import com.beancounter.common.model.Trn
import com.beancounter.common.model.TrnType
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.position.Constants
import com.beancounter.position.Constants.Companion.NASDAQ
import com.beancounter.position.Constants.Companion.hundred
import com.beancounter.position.Constants.Companion.twoK
import com.beancounter.position.StubbedTest
import com.beancounter.position.accumulation.Accumulator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal
import java.util.Optional

private const val EBAY = "EBAY"

/**
 * Integration tests using mocked data from bc-data.
 */
@StubbedTest
internal class FxValuationMvcTests {
    @Autowired
    private lateinit var accumulator: Accumulator

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var valuation: Valuation

    @Autowired
    private lateinit var staticService: StaticService

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var portfolioService: PortfolioServiceClient

    @Autowired
    private lateinit var authConfig: AuthConfig
    lateinit var token: Jwt
    lateinit var tokenUtils: TokenUtils

    @Autowired
    fun setDefaultUser(authConfig: AuthConfig) {
        tokenUtils = TokenUtils(authConfig)
        val user = "user@testing.com"
        token = tokenUtils.getSystemUserToken(SystemUser("user", user))
        mockAuthConfig.login(user)
    }

    private fun getPositions(asset: Asset): Positions {
        val trn = Trn(trnType = TrnType.BUY, asset = asset, quantity = hundred)
        trn.tradeAmount = twoK
        val portfolio = portfolioService.getPortfolioByCode(Constants.TEST)
        val positions = Positions(portfolio, asAt = "2019-10-18")
        accumulator.accumulate(trn, positions)
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
    fun is_MvcValuingPositions() {
        val asset = ebay
        assertThat(asset).hasFieldOrProperty("name")
        val positions = getPositions(asset)
        val positionResponse = PositionResponse(positions)
        assertThat(mockMvc).isNotNull
        val json =
            mockMvc.perform(
                MockMvcRequestBuilders.post("/value")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(objectMapper.writeValueAsString(positionResponse)),
            ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON_VALUE))
                .andReturn().response.contentAsString
        val fromJson = objectMapper.readValue(json, PositionResponse::class.java)
        assertThat(fromJson).isNotNull.hasFieldOrProperty(DATA)
        val jsonPositions = fromJson.data
        assertThat(jsonPositions).isNotNull
        assertThat(jsonPositions.positions).hasSize(positions.positions.size)
        assertThat(jsonPositions.totals.keys)
            .contains(
                Position.In.BASE,
                Position.In.PORTFOLIO,
            )
        var position: Position? = null
        for (key in jsonPositions.positions.keys) {
            position = jsonPositions.positions[key]
            assertThat(position!!.asset)
                .hasFieldOrPropertyWithValue("name", asset.name)
        }
        assertThat(position).isNotNull
        val totalKey = jsonPositions.totals.keys.iterator().next()
        val moneyValues = position!!.moneyValues[totalKey]
        assertThat(moneyValues!!.weight.compareTo(BigDecimal.ONE)).isEqualTo(0)
        val moneyTotal = jsonPositions.totals[totalKey]
        assertThat(moneyTotal)
            .hasFieldOrPropertyWithValue("marketValue", moneyValues.marketValue)
    }

    private val ebay: Asset
        get() {
            val assetInputMap: MutableMap<String, AssetInput> = HashMap()
            assetInputMap["$EBAY:${NASDAQ.code}"] = AssetInput(NASDAQ.code, EBAY)
            val assetRequest = AssetRequest(assetInputMap)
            val assetResponse = assetService.handle(assetRequest)
            assertThat(assetResponse!!.data).hasSize(1)
            return assetResponse.data["$EBAY:${NASDAQ.code}"] ?: error("$EBAY Not Found. This should never happen")
        }

    @Test
    fun is_MvcRestException() {
        val result =
            mockMvc.perform(
                MockMvcRequestBuilders.post("/value")
                    .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString("{asdf}")),
            ).andExpect(MockMvcResultMatchers.status().is4xxClientError).andReturn()
        val someException = Optional.ofNullable(result.resolvedException as HttpMessageNotReadableException)
        assertThat(someException.isPresent).isTrue
    }

    @Test
    fun is_MarketValuationCalculatedAsAt() {
        val asset = ebay

        // We need to have a Quantity in order to get the price, so create a position
        val positions = getValuedPositions(asset)
        assertThat(positions.getOrCreate(asset).getMoneyValues(Position.In.TRADE, asset.market.currency))
            .hasFieldOrPropertyWithValue("unrealisedGain", BigDecimal("8000.00"))
            .hasFieldOrPropertyWithValue("priceData.close", BigDecimal("100.00"))
            .hasFieldOrPropertyWithValue("marketValue", BigDecimal("10000.00"))
            .hasFieldOrPropertyWithValue("totalGain", BigDecimal("8000.00"))
    }

    @Test
    fun is_AssetAndCurrencyHydratedFromValuationRequest() {
        val asset = ebay
        val positions = getValuedPositions(asset)
        val position = positions.getOrCreate(asset)
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
