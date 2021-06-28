package com.beancounter.marketdata.providers

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.PriceRequest.Companion.of
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
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
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaPriceAdapter
import com.beancounter.marketdata.providers.alpha.AlphaService
import com.beancounter.marketdata.providers.wtd.WtdService
import com.beancounter.marketdata.service.MarketDataService
import com.beancounter.marketdata.service.MdFactory
import com.beancounter.marketdata.utils.AlphaMockUtils
import com.beancounter.marketdata.utils.AlphaMockUtils.getAlphaApi
import com.beancounter.marketdata.utils.RegistrationUtils
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Spy
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
 * AlphaApi Test
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

    private val marketCodeUrl = "/assets/{market}/{code}"

    @Test
    @Throws(Exception::class)
    fun is_PriceUpdated() {
        marketDataService.purge()
        assetService.purge()
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(marketCodeUrl, ASX.code, "AMP")
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
        val price = marketDataService.getPriceResponse(AssetInput(data))
        assertThat(price).hasNoNullFieldsOrProperties()
    }

    private val priceSymbolProp = "priceSymbol"
    private val api = "API"
    private val keyProp = "key"
    private val assetProp = "asset"
    private val codeProp = "code"
    private val closeProp = "close"
    private val openProp = "open"
    private val lowProp = "low"
    private val highProp = "high"
    private val priceDateProp = "priceDate"
    private val prevCloseProp = "previousClose"
    private val marketProp = "market"
    private val nameProp = "name"

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
    fun is_MutualFundAssetEnrichedAndPriceReturned() {
        val code = "B6WZJX0"
        val lon = "LON"
        AlphaMockUtils.mockSearchResponse(code, ClassPathResource("/mock/alpha/mf-search.json").file)
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
            .hasFieldOrPropertyWithValue(codeProp, code)
            .hasFieldOrProperty(marketProp)
            .hasFieldOrPropertyWithValue("market.code", lon)
            .hasFieldOrPropertyWithValue(priceSymbolProp, symbol)
            .hasFieldOrPropertyWithValue(nameProp, "AXA Framlington Health Fund Z GBP Acc")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo(symbol)
        val priceResponse = marketDataService.getPriceResponse(AssetInput(data))
        assertThat(priceResponse.data).isNotNull
        assertThat(priceResponse.data.iterator().next())
            .isNotNull
            .hasFieldOrPropertyWithValue(priceDateProp, dateUtils.getDate("2020-05-12"))
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal("3.1620"))
            .hasFieldOrPropertyWithValue(prevCloseProp, BigDecimal("3.1620"))
            .hasFieldOrPropertyWithValue(lowProp, BigDecimal("3.1620"))
            .hasFieldOrPropertyWithValue(highProp, BigDecimal("3.1620"))
            .hasFieldOrPropertyWithValue(openProp, BigDecimal("3.1620"))
    }

    @Test
    @Throws(Exception::class)
    fun is_EnrichedMarketCodeTranslated() {
        val amp = "AMP"
        val mvcResult = mockMvc.perform(
            MockMvcRequestBuilders.get(marketCodeUrl, ASX.code, amp)
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
            .hasFieldOrPropertyWithValue(codeProp, amp)
            .hasFieldOrProperty(marketProp)
            .hasFieldOrPropertyWithValue(priceSymbolProp, "$amp.AUS")
            .hasFieldOrPropertyWithValue(nameProp, "$amp Limited")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo("$amp.AUS")
    }

    @Test
    @Throws(Exception::class)
    fun is_MismatchExchangeEnrichmentHandled() {
        // De-listed asset that can be found on the non-requested market
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        val code = "DTV"
        assetInputMap[keyProp] = AssetInput(NYSE.code, code)
        val (data) = assetService
            .process(AssetRequest(assetInputMap))
        val asset = data[keyProp]
        assertThat(asset!!.priceSymbol).isNull()
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
        val (data1) = objectMapper
            .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data1)
            .isNotNull
            .hasFieldOrPropertyWithValue(nameProp, null)
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

    @Test
    @Throws(Exception::class)
    fun is_ApiInvalidKeyHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageInfo.json").file
        AlphaMockUtils.mockGlobalResponse("$api.KEY", jsonFile)
        val asset = Asset(api, Market("KEY", USD))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID)
        val results = alphaProvider.getMarketData(
            of(asset)
        )
        assertThat(results)
            .isNotNull
            .hasSize(1)
        assertThat(results.iterator().next())
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
    }

    @Test
    @Throws(Exception::class)
    fun is_ApiCallLimitExceededHandled() {
        val nasdaq = marketService.getMarket(NASDAQ.code)
        val asset = Asset("ABC", nasdaq)
        asset.id = asset.code
        AlphaMockUtils.mockGlobalResponse(
            asset.id,
            ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageNote.json").file
        )
        assertThat(asset).isNotNull
        val assetInput = AssetInput(asset)

        val results = mdFactory.getMarketDataProvider(AlphaService.ID)
            .getMarketData(
                of(assetInput)
            )
        assertThat(results)
            .isNotNull
            .hasSize(1)
        val mdpPrice = results.iterator().next()
        assertThat(mdpPrice)
            .hasFieldOrPropertyWithValue(assetProp, asset)
            .hasFieldOrPropertyWithValue(closeProp, BigDecimal.ZERO)
        val priceResponse = marketDataService.getPriceResponse(assetInput)
        assertThat(priceResponse).isNotNull
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
        val asset = Asset("AMP", ASX)
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

    @Test
    @Throws(Exception::class)
    fun is_PriceNotFoundSetIntoAsset() {
        val code = "BWLD"
        AlphaMockUtils.mockSearchResponse(
            code,
            ClassPathResource("/mock/alpha/bwld-search.json").file
        )
        AlphaMockUtils.mockGlobalResponse(
            code,
            ClassPathResource("/mock/alpha/price-not-found.json").file
        )
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        assetInputMap[keyProp] = AssetInput(NYSE.code, code)
        val (data) = assetService
            .process(AssetRequest(assetInputMap))
        val asset = data[keyProp]
        assertThat(asset!!.priceSymbol).isNull()
        val priceResult = priceService.getMarketData(
            asset.id,
            dateUtils.date
        )
        if (priceResult.isPresent) {
            val priceRequest = of(asset)
            val priceResponse = marketDataService.getPriceResponse(priceRequest)
            assertThat(priceResponse).isNotNull.hasFieldOrProperty("data")
            val price = priceResponse.data.iterator().next()
            assertThat(price).hasFieldOrProperty(priceDateProp)
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_BackFillNasdaqIncludingDividendEvent() {
        AlphaMockUtils.mockSearchResponse("KMI", ClassPathResource("/mock/alpha/kmi-search.json").file)
        val file = ClassPathResource("/mock/alpha/kmi-backfill-response.json").file
        AlphaMockUtils.mockAdjustedResponse("KMI", file)
        val alphaMapper = AlphaPriceAdapter().alphaMapper
        val priceResponse = alphaMapper.readValue(file, PriceResponse::class.java)
        assertThat(priceResponse.data).isNotNull
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        assetInputMap[keyProp] = AssetInput(NASDAQ.code, "KMI")
        val (dataMap) = assetService.process(AssetRequest(assetInputMap))
        val asset = dataMap[keyProp]
        assertThat(asset!!.id).isNotNull
        priceService.setEventWriter(mockEventWriter)
        marketDataService.backFill(asset)
        Thread.sleep(300)
        val date = dateUtils.getDate("2020-05-01")
        assertThat(date).isNotNull
        val marketData = priceService.getMarketData(asset.id, date)
        if (marketData.isPresent) {
            marketData.ifPresent { data: MarketData ->
                Mockito.verify(
                    mockEventWriter,
                    Mockito.times(priceResponse.data.size)
                ).write(data)
            }
        }
    }
}
