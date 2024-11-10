package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.input.AssetInput
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.URL_ASSETS_MARKET_CODE
import com.beancounter.marketdata.utils.RegistrationUtils
import com.fasterxml.jackson.module.kotlin.readValue
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import java.math.BigDecimal

/**
 * Enrichment integration tests.
 */
@SpringBootTest
@ActiveProfiles("alpha")
@Tag("wiremock")
@AutoConfigureWireMock(port = 0)
@AutoConfigureMockMvc
@AutoConfigureMockAuth
class AlphaVantageEnrichmentTest {
    @MockBean
    private lateinit var dateUtils: DateUtils

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var alphaConfig: AlphaConfig

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    private lateinit var token: Jwt

    @BeforeEach
    fun mockServices() {
        `when`(dateUtils.isToday(anyString())).thenReturn(true)
        AlphaMockUtils.mockAlphaAssets()
        // Set up a user account
        token = mockAuthConfig.getUserToken(Constants.systemUser)
        RegistrationUtils.registerUser(mockMvc, token)
    }

    @Test
    fun is_MutualFundAssetEnrichedAndPriceReturned() {
        val code = "B6WZJX0"
        val lon = "LON"
        val alphaConfig = AlphaConfig()
        AlphaMockUtils.mockSearchResponse(
            "$code.$lon",
            ClassPathResource("/mock/alpha/mf-search.json").file,
        )
        val symbol = "0P0000XMSV.$lon"
        AlphaMockUtils.mockGlobalResponse(
            symbol,
            ClassPathResource(AlphaMockUtils.ALPHA_MOCK + "/pence-price-response.json").file,
        )

        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(URL_ASSETS_MARKET_CODE, lon, code)
                        .with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(token),
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()

        val (assetResponse) = objectMapper.readValue<AssetResponse>(mvcResult.response.contentAsString)
        assertThat(assetResponse)
            .isNotNull
            .hasFieldOrPropertyWithValue(AlphaConstants.P_CODE, code)
            .hasFieldOrProperty(AlphaConstants.P_MARKET)
            .hasFieldOrPropertyWithValue("market.code", lon)
            .hasFieldOrPropertyWithValue(AlphaConstants.P_SYMBOL, symbol)
            .hasFieldOrPropertyWithValue(AlphaConstants.P_NAME, "AXA Framlington Health Fund Z GBP Acc")

        assertThat(alphaConfig.getPriceCode(assetResponse)).isEqualTo(symbol)
        `when`(dateUtils.offsetDateString(anyString())).thenReturn(DateUtils().offsetDateString())
        val priceResponse = marketDataService.getPriceResponse(PriceRequest.of(assetResponse, date = "today"))
        assertThat(priceResponse.data).isNotNull
        val price = BigDecimal("3.1620")
        assertThat(
            priceResponse.data.iterator().next(),
        ).isNotNull
            .hasFieldOrPropertyWithValue(
                AlphaConstants.P_PRICE_DATE,
                DateUtils().getFormattedDate("2020-05-12"),
            ).hasFieldOrPropertyWithValue(AlphaConstants.P_CLOSE, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.P_PREV_CLOSE, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.P_LOW, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.P_HIGH, price)
            .hasFieldOrPropertyWithValue(AlphaConstants.P_OPEN, price)
    }

    @Test
    fun is_EnrichedMarketCodeTranslated() {
        val amp = "AMP"
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(URL_ASSETS_MARKET_CODE, Constants.ASX.code, amp)
                        .with(
                            SecurityMockMvcRequestPostProcessors.jwt().jwt(token),
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper.readValue<AssetResponse>(mvcResult.response.contentAsString)
        assertThat(data)
            .isNotNull
            .hasFieldOrPropertyWithValue(AlphaConstants.P_CODE, amp)
            .hasFieldOrProperty(AlphaConstants.P_MARKET)
            .hasFieldOrPropertyWithValue(AlphaConstants.P_SYMBOL, "$amp.AUS")
            .hasFieldOrPropertyWithValue(AlphaConstants.P_NAME, "$amp Limited")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo("$amp.AUS")
    }

    @Test
    fun is_MismatchExchangeEnrichmentHandled() {
        // De-listed asset that can be found on the non-requested market
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        val code = "DTV"
        assetInputMap[AlphaConstants.P_KEY] = AssetInput(Constants.NYSE.code, code)
        val (data) = assetService.handle(AssetRequest(assetInputMap))
        val asset = data[AlphaConstants.P_KEY]
        assertThat(asset!!.priceSymbol).isNull()
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(URL_ASSETS_MARKET_CODE, Constants.NYSE.code, code)
                        .with(
                            SecurityMockMvcRequestPostProcessors.jwt().jwt(token),
                        ).contentType(MediaType.APPLICATION_JSON),
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data1) = objectMapper.readValue<AssetResponse>(mvcResult.response.contentAsString)
        assertThat(data1).isNotNull.hasFieldOrPropertyWithValue(AlphaConstants.P_NAME, null)
    }
}
