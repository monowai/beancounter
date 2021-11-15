package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.config.PriceSchedule
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.marketCodeUrl
import com.beancounter.marketdata.utils.RegistrationUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal

/**
 * Enrichment integration tests.
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("alpha")
@Tag("slow")
@AutoConfigureWireMock(port = 0)
class AlphaVantageEnrichmentTest {

    private val authorityRoleConverter = AuthorityRoleConverter()
    private val tokenUtils: TokenUtils = TokenUtils()
    private val dateUtils = DateUtils()
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var mdFactory: MdFactory

    @Autowired
    private lateinit var marketService: MarketService

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var alphaConfig: AlphaConfig

    @Autowired
    private lateinit var priceService: PriceService

    @Autowired
    private lateinit var priceSchedule: PriceSchedule

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc
    private lateinit var token: Jwt

    @Autowired
    @Throws(Exception::class)
    fun mockServices() {
        AlphaMockUtils.getAlphaApi()
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()

        // Set up a user account
        token = tokenUtils.getUserToken(Constants.systemUser)
        RegistrationUtils.registerUser(mockMvc, token)
    }

    @Test
    @Throws(Exception::class)
    fun is_MutualFundAssetEnrichedAndPriceReturned() {
        val code = "B6WZJX0"
        val lon = "LON"
        val alphaConfig = AlphaConfig()
        AlphaMockUtils.mockSearchResponse("$code.$lon", ClassPathResource("/mock/alpha/mf-search.json").file)
        val symbol = "0P0000XMSV.$lon"
        AlphaMockUtils.mockGlobalResponse(
            symbol, ClassPathResource(AlphaMockUtils.alphaContracts + "/pence-price-response.json").file
        )

        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(marketCodeUrl, lon, code)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data)
            .isNotNull
            .hasFieldOrPropertyWithValue(AlphaConstants.codeProp, code)
            .hasFieldOrProperty(AlphaConstants.marketProp)
            .hasFieldOrPropertyWithValue("market.code", lon)
            .hasFieldOrPropertyWithValue(AlphaConstants.priceSymbolProp, symbol)
            .hasFieldOrPropertyWithValue(AlphaConstants.nameProp, "AXA Framlington Health Fund Z GBP Acc")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo(symbol)
        val priceResponse = marketDataService.getPriceResponse(PriceRequest.of(data))
        assertThat(priceResponse.data).isNotNull
        val price = BigDecimal("3.1620")
        assertThat(priceResponse.data.iterator().next())
            .isNotNull
            .hasFieldOrPropertyWithValue(AlphaConstants.priceDateProp, dateUtils.getDate("2020-05-12"))
            .hasFieldOrPropertyWithValue(AlphaConstants.closeProp, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.prevCloseProp, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.lowProp, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.highProp, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.openProp, price)
    }

    @Test
    @Throws(Exception::class)
    fun is_EnrichedMarketCodeTranslated() {
        val amp = "AMP"
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(marketCodeUrl, Constants.ASX.code, amp)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data)
            .isNotNull
            .hasFieldOrPropertyWithValue(AlphaConstants.codeProp, amp)
            .hasFieldOrProperty(AlphaConstants.marketProp)
            .hasFieldOrPropertyWithValue(AlphaConstants.priceSymbolProp, "$amp.AUS")
            .hasFieldOrPropertyWithValue(AlphaConstants.nameProp, "$amp Limited")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo("$amp.AUS")
    }

    @Test
    @Throws(Exception::class)
    fun is_MismatchExchangeEnrichmentHandled() {
        // De-listed asset that can be found on the non-requested market
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        val code = "DTV"
        assetInputMap[AlphaConstants.keyProp] = AssetInput(Constants.NYSE.code, code)
        val (data) = assetService
            .process(AssetRequest(assetInputMap))
        val asset = data[AlphaConstants.keyProp]
        assertThat(asset!!.priceSymbol).isNull()
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(marketCodeUrl, Constants.NYSE.code, code)
                .with(
                    SecurityMockMvcRequestPostProcessors.jwt()
                        .jwt(token).authorities(authorityRoleConverter)
                )
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data1) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data1)
            .isNotNull
            .hasFieldOrPropertyWithValue(AlphaConstants.nameProp, null)
    }
}
