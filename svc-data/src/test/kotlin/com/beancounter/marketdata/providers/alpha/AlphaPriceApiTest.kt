package com.beancounter.marketdata.providers.alpha

import com.beancounter.auth.MockAuthConfig
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.Payload.Companion.DATA
import com.beancounter.common.contracts.PriceRequest.Companion.of
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils.Companion.getTestAsset
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.Constants.Companion.MSFT
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.event.EventProducer
import com.beancounter.marketdata.providers.MarketDataService
import com.beancounter.marketdata.providers.MdFactory
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.API
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_ASSET
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_CLOSE
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_CODE
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_HIGH
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_KEY
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_LOW
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_MARKET
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_NAME
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_OPEN
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_PREV_CLOSE
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_PRICE_DATE
import com.beancounter.marketdata.providers.alpha.AlphaConstants.Companion.P_SYMBOL
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.URL_ASSETS_MARKET_CODE
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.mockAdjustedResponse
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.mockAlphaAssets
import com.beancounter.marketdata.providers.alpha.AlphaMockUtils.mockSearchResponse
import com.beancounter.marketdata.providers.marketstack.MarketStackService
import com.beancounter.marketdata.trn.CashTrnServices
import com.beancounter.marketdata.utils.DateUtilsMocker
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * AlphaApi business functional
 *
 * @author mikeh
 * @since 2019-03-04
 */
@SpringBootTest(classes = [MarketDataBoot::class, MockAuthConfig::class])
@ActiveProfiles(
    "h2db",
    "alpha"
)
@Tag("wiremock")
@AutoConfigureWireMock(port = 0)
internal class AlphaPriceApiTest {
    @MockitoBean
    private lateinit var dateUtils: DateUtils

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var mdFactory: MdFactory

    @Autowired
    private lateinit var marketDataService: MarketDataService

    @Autowired
    private lateinit var alphaConfig: AlphaConfig

    @Autowired
    private lateinit var priceService: PriceService

    @MockitoBean
    private lateinit var cashTrnServices: CashTrnServices

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var context: WebApplicationContext

    @MockitoSpyBean
    private lateinit var mockEventProducer: EventProducer
    private lateinit var mockMvc: MockMvc
    private lateinit var token: Jwt

    @BeforeEach
    fun mockServices() {
        DateUtilsMocker.mockToday(dateUtils)
        mockAlphaAssets()
        mockMvc =
            MockMvcBuilders
                .webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        // Set up a user account
        token = mockAuthConfig.getUserToken(Constants.systemUser)
        RegistrationUtils.registerUser(
            mockMvc,
            token
        )
    }

    private val amp = "AMP"

    @Test
    fun is_BrkBTranslated() {
        val code = "BRK.B"
        val mvcResult =
            mockMvc
                .perform(
                    MockMvcRequestBuilders
                        .get(
                            URL_ASSETS_MARKET_CODE,
                            NYSE.code,
                            code
                        ).with(
                            SecurityMockMvcRequestPostProcessors
                                .jwt()
                                .jwt(token)
                        ).contentType(MediaType.APPLICATION_JSON)
                ).andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) =
            objectMapper
                .readValue(
                    mvcResult.response.contentAsString,
                    AssetResponse::class.java
                )
        val brkB = "BRK-B"
        assertThat(data)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_CODE,
                code
            ).hasFieldOrProperty(P_MARKET)
            .hasFieldOrPropertyWithValue(
                P_SYMBOL,
                brkB
            ).hasFieldOrPropertyWithValue(
                P_NAME,
                "Berkshire Hathaway Inc."
            )
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo(brkB)
    }

    @Test
    fun is_EmptyGlobalResponseHandled() {
        val jsonFile = ClassPathResource("${AlphaMockUtils.ALPHA_MOCK}/global-empty.json").file
        AlphaMockUtils.mockGlobalResponse(
            "$API.EMPTY",
            jsonFile
        )
        val asset =
            Asset(
                code = API,
                market =
                    Market(
                        "EMPTY",
                        USD.code
                    )
            )

        assertNotNull {
            mdFactory
                .getMarketDataProvider(AlphaPriceService.ID)
                .getMarketData(of(asset))
        }
    }

    private val changeProp = "change"

    @Test
    fun is_CurrentPriceFound() {
        val jsonFile = ClassPathResource("${AlphaMockUtils.ALPHA_MOCK}/global-response.json").file
        AlphaMockUtils.mockGlobalResponse(
            MSFT.code,
            jsonFile
        )
        val nasdaq =
            Market(
                NASDAQ.code,
                USD.code
            )
        val asset =
            getTestAsset(
                market = nasdaq,
                code = MSFT.code
            )
        val priceRequest = of(asset = asset)
        val mdResult =
            mdFactory
                .getMarketDataProvider(AlphaPriceService.ID)
                .getMarketData(priceRequest)

        // Coverage - AV does not support this market, but we expect a price
        assertThat(
            mdFactory.getMarketDataProvider(MarketStackService.ID).isMarketSupported(nasdaq)
        ).isFalse
        val marketData = mdResult.iterator().next()
        assertThat(marketData)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_ASSET,
                asset
            ).hasFieldOrProperty(P_CLOSE)
            .hasFieldOrProperty(P_OPEN)
            .hasFieldOrProperty(P_LOW)
            .hasFieldOrProperty(P_HIGH)
            .hasFieldOrProperty(P_PREV_CLOSE)
            .hasFieldOrProperty(changeProp)
    }

    @Test
    fun is_CurrentPriceAsxFound() {
        val asset =
            getTestAsset(
                code = amp,
                market = ASX
            )
        val priceRequest = of(asset)
        val mdResult =
            mdFactory
                .getMarketDataProvider(AlphaPriceService.ID)
                .getMarketData(priceRequest)
        val marketData = mdResult.iterator().next()
        assertThat(marketData)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                P_ASSET,
                asset
            ).hasFieldOrProperty(P_CLOSE)
            .hasFieldOrProperty(P_OPEN)
            .hasFieldOrProperty(P_LOW)
            .hasFieldOrProperty(P_HIGH)
            .hasFieldOrProperty(P_PREV_CLOSE)
            .hasFieldOrProperty(changeProp)
    }

    private val mockAlpha = "/mock/alpha"

    @Test
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
        val (data) =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            Pair(
                                P_KEY,
                                AssetInput(
                                    NYSE.code,
                                    code
                                )
                            )
                        )
                    )
                )
        val asset = data[P_KEY]
        assertThat(asset!!.priceSymbol).isNull()
        val priceResult =
            priceService.getMarketData(
                asset.id,
                DateUtils().date
            )
        if (priceResult.isPresent) {
            val priceResponse = marketDataService.getPriceResponse(of(asset))
            assertThat(priceResponse).isNotNull.hasFieldOrProperty(DATA)
            val price = priceResponse.data.iterator().next()
            assertThat(price).hasFieldOrProperty(P_PRICE_DATE)
        }
    }

    @Test
    fun is_BackFillWritingDividendEvent() {
        priceService.setEventWriter(mockEventProducer)
        val assetCode = "KMI"
        mockSearchResponse(
            assetCode,
            ClassPathResource("$mockAlpha/kmi-search.json").file
        )
        val file = ClassPathResource("$mockAlpha/kmi-backfill-response.json").file
        mockAdjustedResponse(
            assetCode,
            file
        )
        val asset =
            assetService
                .handle(
                    AssetRequest(
                        mapOf(
                            Pair(
                                P_KEY,
                                AssetInput(
                                    NASDAQ.code,
                                    assetCode
                                )
                            )
                        )
                    )
                ).data[P_KEY]
        assertThat(asset).isNotNull.hasFieldOrProperty("id")
        marketDataService.backFill(asset!!.id)
        Thread.sleep(300)
        Mockito
            .verify(
                mockEventProducer,
                // Found the Dividend request; ignored the prices
                Mockito.times(1)
            ).write(any())
    }
}