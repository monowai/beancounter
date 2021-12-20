package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.contracts.PriceRequest.Companion.of
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.config.PriceSchedule
import com.beancounter.marketdata.event.EventWriter
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.api
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.assetProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.closeProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.codeProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.highProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.keyProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.lowProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.marketProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.nameProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.openProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.prevCloseProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.priceDateProp
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.priceSymbolProp
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.getAlphaApi
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.marketCodeUrl
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.mockAdjustedResponse
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.mockSearchResponse
import com.beancounter.marketdata.providers.wtd.WtdService
import com.beancounter.marketdata.trn.CashServices
import com.beancounter.marketdata.utils.RegistrationUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Spy
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
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
 * AlphaApi business functional
 *
 * @author mikeh
 * @since 2019-03-04
 */
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("alpha")
@Tag("slow")
@AutoConfigureWireMock(port = 0)
internal class AlphaVantageApiTest {
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

    @MockBean
    private lateinit var cashServices: CashServices

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var context: WebApplicationContext

    @Spy
    private lateinit var mockEventWriter: EventWriter
    private lateinit var mockMvc: MockMvc
    private lateinit var token: Jwt

    @Autowired
    @Throws(Exception::class)
    fun mockServices() {
        getAlphaApi()
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
            .build()

        // Setup a user account
        token = tokenUtils.getUserToken(Constants.systemUser)
        RegistrationUtils.registerUser(mockMvc, token)
    }

    private val amp = "AMP"

    @Test
    @Throws(Exception::class)
    fun is_PriceUpdated() {
        marketDataService.purge()
        assetService.purge()
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(marketCodeUrl, ASX.code, amp)
                .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                .contentType(MediaType.APPLICATION_JSON)
        )
            .andExpect(MockMvcResultMatchers.status().isOk)
            .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
            .andReturn()
        val (data) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        priceSchedule.updatePrices()
        Thread.sleep(2000) // Async reads/writes
        val price = marketDataService.getPriceResponse(of(AssetInput(data)))
        assertThat(price).hasNoNullFieldsOrProperties()
    }

    @Test
    @Throws(Exception::class)
    fun is_BrkBTranslated() {
        val code = "BRK.B"
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(marketCodeUrl, NYSE.code, code)
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
        val brkB = "BRK-B"
        assertThat(data)
            .isNotNull
            .hasFieldOrPropertyWithValue(codeProp, code)
            .hasFieldOrProperty(marketProp)
            .hasFieldOrPropertyWithValue(priceSymbolProp, brkB)
            .hasFieldOrPropertyWithValue(nameProp, "Berkshire Hathaway Inc.")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo(brkB)
    }

    @Test
    @Throws(Exception::class)
    fun is_ApiErrorMessageHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageError.json").file
        AlphaMockUtils.mockGlobalResponse("$api.ERR", jsonFile)
        val asset = Asset(api, Market("ERR", USD))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID)
        val results = alphaProvider.getMarketData(of(asset))
        assertThat(results)
            .isNotNull
            .hasSize(1)
        assertThat(results.iterator().next())
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
    }

    @Test
    @Throws(Exception::class)
    fun is_EmptyGlobalResponseHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/global-empty.json").file
        AlphaMockUtils.mockGlobalResponse("$api.EMPTY", jsonFile)
        val asset = Asset(api, Market("EMPTY", USD))
        val results = mdFactory
            .getMarketDataProvider(AlphaService.ID)
            .getMarketData(of(asset))

        assertThat(results) // Contains a default price
            .isNotNull
            .hasSize(1)
        assertThat(results.iterator().next())
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
            .hasFieldOrProperty(assetProp)
    }

    private val changeProp = "change"

    @Test
    @Throws(Exception::class)
    fun is_CurrentPriceFound() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/global-response.json").file
        AlphaMockUtils.mockGlobalResponse(MSFT.code, jsonFile)
        val nasdaq = Market(NASDAQ.code, USD)
        val asset = Asset(MSFT.code, nasdaq)
        val priceRequest = of(asset = asset)
        val mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
            .getMarketData(priceRequest)

        // Coverage - WTD does not support this market
        assertThat(mdFactory.getMarketDataProvider(WtdService.ID).isMarketSupported(nasdaq)).isFalse
        val marketData = mdResult.iterator().next()
        assertThat(marketData)
            .isNotNull
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrProperty(closeProp)
            .hasFieldOrProperty(openProp)
            .hasFieldOrProperty(lowProp)
            .hasFieldOrProperty(highProp)
            .hasFieldOrProperty(prevCloseProp)
            .hasFieldOrProperty(changeProp)
    }

    @Test
    fun is_CurrentPriceAsxFound() {
        val asset = Asset(amp, ASX)
        val priceRequest = of(asset)
        val mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
            .getMarketData(priceRequest)
        val marketData = mdResult.iterator().next()
        assertThat(marketData)
            .isNotNull
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrProperty(closeProp)
            .hasFieldOrProperty(openProp)
            .hasFieldOrProperty(lowProp)
            .hasFieldOrProperty(highProp)
            .hasFieldOrProperty(prevCloseProp)
            .hasFieldOrProperty(changeProp)
    }

    private val mockAlpha = "/mock/alpha"

    @Test
    @Throws(Exception::class)
    fun is_PriceNotFoundSetIntoAsset() {
        val code = "BWLD"
        mockSearchResponse(
            code,
            ClassPathResource("$mockAlpha/bwld-search.json").file
        )
        AlphaMockUtils.mockGlobalResponse(
            code,
            ClassPathResource("$mockAlpha/price-not-found.json").file
        )
        val (data) = assetService
            .process(AssetRequest(mapOf(Pair(keyProp, AssetInput(NYSE.code, code)))))
        val asset = data[keyProp]
        assertThat(asset!!.priceSymbol).isNull()
        val priceResult = priceService.getMarketData(
            asset,
            dateUtils.date
        )
        if (priceResult.isPresent) {
            val priceResponse = marketDataService.getPriceResponse(of(asset))
            assertThat(priceResponse).isNotNull.hasFieldOrProperty(DATA)
            val price = priceResponse.data.iterator().next()
            assertThat(price).hasFieldOrProperty(priceDateProp)
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_BackFillWritingDividendEvent() {
        priceService.setEventWriter(mockEventWriter)
        val assetCode = "KMI"
        mockSearchResponse(assetCode, ClassPathResource("$mockAlpha/kmi-search.json").file)
        val file = ClassPathResource("$mockAlpha/kmi-backfill-response.json").file
        mockAdjustedResponse(assetCode, file)
        val asset =
            assetService.process(
                AssetRequest(mapOf(Pair(keyProp, AssetInput(NASDAQ.code, assetCode))))
            ).data[keyProp]
        assertThat(asset).isNotNull.hasFieldOrProperty("id")
        marketDataService.backFill(asset!!)
        Thread.sleep(300)
        Mockito.verify(
            mockEventWriter,
            Mockito.times(1) // Found the Dividend request; ignored the prices
        ).write(any())
    }
}
