package com.beancounter.marketdata.integ

import com.beancounter.auth.common.TokenUtils
import com.beancounter.auth.server.AuthorityRoleConverter
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.PriceRequest.Companion.of
import com.beancounter.common.contracts.PriceResponse
import com.beancounter.common.input.AssetInput
import com.beancounter.common.model.*
import com.beancounter.common.model.Currency
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.event.EventWriter
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.PriceService
import com.beancounter.marketdata.providers.ScheduledValuation
import com.beancounter.marketdata.providers.alpha.AlphaConfig
import com.beancounter.marketdata.providers.alpha.AlphaPriceAdapter
import com.beancounter.marketdata.providers.alpha.AlphaService
import com.beancounter.marketdata.providers.wtd.WtdService
import com.beancounter.marketdata.service.MarketDataService
import com.beancounter.marketdata.service.MdFactory
import com.beancounter.marketdata.utils.AlphaMockUtils
import com.beancounter.marketdata.utils.RegistrationUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Spy
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.math.BigDecimal
import java.util.*

/**
 * .
 *
 * @author mikeh
 * @since 2019-03-04
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [MarketDataBoot::class])
@ActiveProfiles("alpha")
@Tag("slow")
internal class AlphaVantageApiTest {
    private val authorityRoleConverter = AuthorityRoleConverter()
    private val dateUtils = DateUtils()

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
    private lateinit var scheduledValuation: ScheduledValuation

    @Autowired
    private lateinit var assetService: AssetService

    @Autowired
    private lateinit var context: WebApplicationContext

    @Spy
    private lateinit var mockEventWriter: EventWriter
    private lateinit var mockMvc: MockMvc
    private lateinit var token: Jwt

    companion object {
        val alpha = AlphaMockUtils.getAlphaApi()

        @BeforeAll
        @JvmStatic
        fun is_ApiRunning() {
            assertThat(alpha).isNotNull
            assertThat(alpha.isRunning).isTrue()
        }
    }

    @Autowired
    @Throws(Exception::class)
    fun mockServices() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply<DefaultMockMvcBuilder>(SecurityMockMvcConfigurers.springSecurity())
                .build()

        // Setup a user account
        val user = SystemUser("user", "user@testing.com")
        token = TokenUtils.getUserToken(user)
        RegistrationUtils.registerUser(mockMvc, token)
    }

    @Test
    @Throws(Exception::class)
    fun is_PriceUpdated() {
        marketDataService.purge()
        assetService.purge()
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{market}/{code}", "ASX", "AMP")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        scheduledValuation.updatePrices()
        Thread.sleep(2000) // Async reads/writes
        val price = marketDataService.getPriceResponse(data)
        assertThat(price).hasNoNullFieldsOrProperties()
    }

    @Test
    @Throws(Exception::class)
    fun is_BrkBTranslated() {
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{market}/{code}", "NYSE", "BRK.B")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data)
                .isNotNull
                .hasFieldOrPropertyWithValue("code", "BRK.B")
                .hasFieldOrProperty("market")
                .hasFieldOrPropertyWithValue("priceSymbol", "BRK-B")
                .hasFieldOrPropertyWithValue("name", "Berkshire Hathaway Inc.")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo("BRK-B")
    }

    @Test
    @Throws(Exception::class)
    fun is_MutualFundAssetEnrichedAndPriceReturned() {
        AlphaMockUtils.mockSearchResponse("B6WZJX0", ClassPathResource("/contracts/alpha/mf-search.json").file)
        AlphaMockUtils.mockGlobalResponse(
                "0P0000XMSV.LON", ClassPathResource(AlphaMockUtils.alphaContracts + "/pence-price-response.json").file)
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{market}/{code}", "LON", "B6WZJX0")
                        .with(SecurityMockMvcRequestPostProcessors.jwt().jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data)
                .isNotNull
                .hasFieldOrPropertyWithValue("code", "B6WZJX0")
                .hasFieldOrProperty("market")
                .hasFieldOrPropertyWithValue("market.code", "LON")
                .hasFieldOrPropertyWithValue("priceSymbol", "0P0000XMSV.LON")
                .hasFieldOrPropertyWithValue("name", "AXA Framlington Health Fund Z GBP Acc")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo("0P0000XMSV.LON")
        val priceResponse = marketDataService.getPriceResponse(data)
        assertThat(priceResponse.data).isNotNull
        assertThat(priceResponse.data.iterator().next())
                .isNotNull
                .hasFieldOrPropertyWithValue("priceDate", dateUtils.getDate("2020-05-12"))
                .hasFieldOrPropertyWithValue("close", BigDecimal("3.1620"))
                .hasFieldOrPropertyWithValue("previousClose", BigDecimal("3.1620"))
                .hasFieldOrPropertyWithValue("low", BigDecimal("3.1620"))
                .hasFieldOrPropertyWithValue("high", BigDecimal("3.1620"))
                .hasFieldOrPropertyWithValue("open", BigDecimal("3.1620"))
    }

    @Test
    @Throws(Exception::class)
    fun is_EnrichedMarketCodeTranslated() {
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{market}/{code}", "ASX", "AMP")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data) = objectMapper
                .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data)
                .isNotNull
                .hasFieldOrPropertyWithValue("code", "AMP")
                .hasFieldOrProperty("market")
                .hasFieldOrPropertyWithValue("priceSymbol", "AMP.AUS")
                .hasFieldOrPropertyWithValue("name", "AMP Limited")
        assertThat(alphaConfig.getPriceCode(data)).isEqualTo("AMP.AUS")
    }

    @Test
    @Throws(Exception::class)
    fun is_MismatchExchangeEnrichmentHandled() {
        // De-listed asset that can be found on the non-requested market
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        assetInputMap["key"] = AssetInput("NYSE", "DTV")
        val (data) = assetService
                .process(AssetRequest(assetInputMap))
        val asset = data["key"]
        assertThat(asset!!.priceSymbol).isNull()
        val mvcResult = mockMvc.perform(
                MockMvcRequestBuilders.get("/assets/{market}/{code}", "NYSE", "DTV")
                        .with(SecurityMockMvcRequestPostProcessors.jwt()
                                .jwt(token).authorities(authorityRoleConverter))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk)
                .andExpect(MockMvcResultMatchers.content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
        val (data1) = objectMapper
                .readValue(mvcResult.response.contentAsString, AssetResponse::class.java)
        assertThat(data1)
                .isNotNull
                .hasFieldOrPropertyWithValue("name", null)
    }

    @Test
    @Throws(Exception::class)
    fun is_ApiErrorMessageHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageError.json").file
        AlphaMockUtils.mockGlobalResponse("API.ERR", jsonFile)
        val asset = Asset("API", Market("ERR", Currency("USD")))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID)
        val results = alphaProvider.getMarketData(of(asset))
        assertThat(results)
                .isNotNull
                .hasSize(1)
        assertThat(results.iterator().next())
                .hasFieldOrPropertyWithValue("asset", asset)
                .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO)
    }

    @Test
    @Throws(Exception::class)
    fun is_ApiInvalidKeyHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageInfo.json").file
        AlphaMockUtils.mockGlobalResponse("API.KEY", jsonFile)
        val asset = Asset("API", Market("KEY", Currency("USD")))
        val alphaProvider = mdFactory.getMarketDataProvider(AlphaService.ID)
        val results = alphaProvider.getMarketData(
                of(asset))
        assertThat(results)
                .isNotNull
                .hasSize(1)
        assertThat(results.iterator().next())
                .hasFieldOrPropertyWithValue("asset", asset)
                .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO)
    }

    @Test
    @Throws(Exception::class)
    fun is_ApiCallLimitExceededHandled() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/alphavantageNote.json").file
        val nasdaq = marketService.getMarket("NASDAQ")
        AlphaMockUtils.mockGlobalResponse("ABC", jsonFile)
        val asset = Asset("ABC", nasdaq)
        val results = mdFactory.getMarketDataProvider(AlphaService.ID)
                .getMarketData(of(asset))
        assertThat(results)
                .isNotNull
                .hasSize(1)
        val mdpPrice = results.iterator().next()
        assertThat(mdpPrice)
                .hasFieldOrPropertyWithValue("asset", asset)
                .hasFieldOrPropertyWithValue("close", BigDecimal.ZERO)
        val priceResponse = marketDataService.getPriceResponse(asset)
        assertThat(priceResponse).isNotNull
    }

    @Test
    @Throws(Exception::class)
    fun is_CurrentPriceFound() {
        val jsonFile = ClassPathResource(AlphaMockUtils.alphaContracts + "/global-response.json").file
        AlphaMockUtils.mockGlobalResponse("MSFT", jsonFile)
        val nasdaq = Market("NASDAQ", Currency("USD"))
        val asset = Asset("MSFT", nasdaq)
        val priceRequest = of(asset)
        val mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
                .getMarketData(priceRequest)

        // Coverage - WTD does not support this market
        assertThat(mdFactory.getMarketDataProvider(WtdService.ID).isMarketSupported(nasdaq)).isFalse()
        val marketData = mdResult.iterator().next()
        assertThat(marketData)
                .isNotNull
                .hasFieldOrPropertyWithValue("asset", asset)
                .hasFieldOrProperty("close")
                .hasFieldOrProperty("open")
                .hasFieldOrProperty("low")
                .hasFieldOrProperty("high")
                .hasFieldOrProperty("previousClose")
                .hasFieldOrProperty("change")
    }

    @Test
    fun is_CurrentPriceAsxFound() {
        val asset = Asset("AMP", Market("ASX", Currency("AUD")))
        val priceRequest = of(asset)
        val mdResult = mdFactory.getMarketDataProvider(AlphaService.ID)
                .getMarketData(priceRequest)
        val marketData = mdResult.iterator().next()
        assertThat(marketData)
                .isNotNull
                .hasFieldOrPropertyWithValue("asset", asset)
                .hasFieldOrProperty("close")
                .hasFieldOrProperty("open")
                .hasFieldOrProperty("low")
                .hasFieldOrProperty("high")
                .hasFieldOrProperty("previousClose")
                .hasFieldOrProperty("change")
    }

    @Test
    @Throws(Exception::class)
    fun is_PriceNotFoundSetIntoAsset() {
        AlphaMockUtils.mockSearchResponse("BWLD",
                ClassPathResource("/contracts/alpha/bwld-search.json").file)
        AlphaMockUtils.mockGlobalResponse("BWLD",
                ClassPathResource("/contracts/alpha/price-not-found.json").file)
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        assetInputMap["key"] = AssetInput("NYSE", "BWLD")
        val (data) = assetService
                .process(AssetRequest(assetInputMap))
        val asset = data["key"]
        assertThat(asset!!.priceSymbol).isNull()
        val priceResult = priceService.getMarketData(
                asset.id,
                dateUtils.date)
        if (priceResult != null && priceResult.isPresent) {
            val priceRequest = of(asset)
            val priceResponse = marketDataService.getPriceResponse(priceRequest)
            assertThat(priceResponse).isNotNull.hasFieldOrProperty("data")
            val price = Objects.requireNonNull(priceResponse.data)!!.iterator().next()
            assertThat(price).hasFieldOrProperty("priceDate")
        }
    }

    @Test
    @Throws(Exception::class)
    fun is_BackFillNasdaqIncludingDividendEvent() {
        AlphaMockUtils.mockSearchResponse("KMI", ClassPathResource("/contracts/alpha/kmi-search.json").file)
        val file = ClassPathResource("/contracts/alpha/kmi-backfill-response.json").file
        AlphaMockUtils.mockAdjustedResponse("KMI", file)
        val alphaMapper = AlphaPriceAdapter().alphaMapper
        val priceResponse = alphaMapper.readValue(file, PriceResponse::class.java)
        assertThat(priceResponse.data).isNotNull
        val assetInputMap: MutableMap<String, AssetInput> = HashMap()
        assetInputMap["key"] = AssetInput("NASDAQ", "KMI")
        val (dataMap) = assetService.process(AssetRequest(assetInputMap))
        val asset = dataMap["key"]
        assertThat(asset!!.id).isNotNull()
        priceService.setEventWriter(mockEventWriter)
        marketDataService.backFill(asset)
        Thread.sleep(300)
        val date = dateUtils.getDate("2020-05-01")
        assertThat(date).isNotNull()
        val marketData = priceService.getMarketData(asset.id, date)
        if (marketData != null && marketData.isPresent) {
            marketData.ifPresent { data: MarketData ->
                Mockito.verify(mockEventWriter,
                        Mockito.times(priceResponse.data.size)).write(data)
            }
        }
    }

}