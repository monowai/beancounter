package com.beancounter.marketdata.contracts

import com.beancounter.auth.common.TokenUtils
import com.beancounter.common.contracts.AssetRequest
import com.beancounter.common.contracts.AssetResponse
import com.beancounter.common.contracts.AssetUpdateResponse
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.contracts.RegistrationResponse
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.AssetUtils.Companion.getAsset
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioService
import com.beancounter.marketdata.providers.fxrates.EcbRates
import com.beancounter.marketdata.providers.fxrates.FxGateway
import com.beancounter.marketdata.providers.wtd.WtdGateway
import com.beancounter.marketdata.providers.wtd.WtdMarketData
import com.beancounter.marketdata.providers.wtd.WtdResponse
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.EcbMockUtils.get
import com.beancounter.marketdata.utils.EcbMockUtils.getRateMap
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.assertj.core.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.io.File
import java.io.IOException
import java.math.BigDecimal
import java.util.HashMap
import java.util.Objects

/**
 * Spring Contract base class.  Mocks out calls to various gateways that can be imported
 * and run as stubs in other services.  Any data required from an integration call in a
 * dependent service, should be mocked in this class.
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    properties = ["auth.enabled=false"],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@DirtiesContext
@AutoConfigureMessageVerifier
@AutoConfigureWireMock(port = 0)
@WebAppConfiguration
@ActiveProfiles("contracts")
class ContractVerifierBase {
    @Autowired
    private lateinit var dateUtils: DateUtils
    private val objectMapper = BcJson().objectMapper

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockBean
    private lateinit var fxGateway: FxGateway

    @MockBean
    private lateinit var wtdGateway: WtdGateway

    @MockBean
    private lateinit var portfolioService: PortfolioService

    @MockBean
    private lateinit var trnService: TrnService

    @MockBean
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var systemUserService: SystemUserService

    @Autowired
    private lateinit var context: WebApplicationContext

    @BeforeEach
    @Throws(Exception::class)
    fun initMocks() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        mockPortfolios()
        systemUsers()
        ecbRates()
        mockTrnGetResponses()
        mockAssets()
    }

    @Throws(Exception::class)
    private fun systemUsers() {
        val jsonFile = ClassPathResource("contracts/register/response.json").file
        val response = objectMapper.readValue(jsonFile, RegistrationResponse::class.java)
        val email = "blah@blah.com"
        val jwt = TokenUtils().getUserToken(SystemUser(email, email))
        Mockito.`when`(jwtDecoder.decode(email)).thenReturn(jwt)
        SecurityContextHolder.getContext().authentication =
            JwtAuthenticationToken(
                jwtDecoder.decode(
                    email
                )
            )
        Mockito.`when`(systemUserService.register(jwt)).thenReturn(response)
    }

    private fun ecbRates() {
        var rates: Map<String, BigDecimal> = getRateMap(
            "0.8973438622", "1.3652189519", "0.7756191673",
            "1.5692749462", "1.4606963388"
        )
        mockEcbRates(rates, get("2019-10-20", rates))
        rates = getRateMap(
            "0.8973438622", "1.3652189519", "0.7756191673",
            "10.0", "1.4606963388"
        )
        mockEcbRates(rates, get("2019-01-01", rates))
        rates = getRateMap(
            "0.8973438622", "1.3652189519", "0.7756191673",
            "1.5692749462", "1.4606963388"
        )
        mockEcbRates(rates, get("2019-10-18", rates))

        // Current
        mockEcbRates(rates, get(dateUtils.today(), rates))
        rates = getRateMap(
            "0.897827258", "1.3684683067", "0.8047495062",
            "1.5053869635", "1.4438857964"
        )
        mockEcbRates(rates, get("2019-07-26", rates))
        // Saturday results are the same as Fridays
        mockEcbRates(rates, get("2019-07-26", rates), "2019-07-27")
        rates = getRateMap(
            "0.9028530155", "1.3864210906", "0.8218941856",
            "1.5536294691", "1.4734561213"
        )
        mockEcbRates(rates, get("2019-08-16", rates))
        rates = getRateMap(
            "0.9078529278", "1.36123468", "0.7791193827",
            "1.5780299591", "1.460463005"
        )
        mockEcbRates(rates, get("2019-11-12", rates))
        rates = getRateMap(
            "0.8482483671", "1.6586648571", "0.6031894139",
            "1.8855712953", "1.6201543812"
        )
        mockEcbRates(rates, get("1999-01-04", rates))
    }

    @Throws(Exception::class)
    private fun mockTrnGetResponses() {
        mockTrnPostResponse(testPortfolio)
        mockTrnGetResponse(testPortfolio, "contracts/trn/TEST-response.json")
        mockTrnGetResponse(emptyPortfolio, "contracts/trn/EMPTY-response.json")
        Mockito.`when`(
            trnService.findByPortfolioAsset(
                testPortfolio,
                "KMI",
                Objects.requireNonNull(
                    dateUtils.getDate("2020-05-01", dateUtils.getZoneId())
                )
            )
        )
            .thenReturn(
                objectMapper.readValue(
                    ClassPathResource("contracts/trn/trn-for-asset.json").file,
                    TrnResponse::class.java
                )
            )
    }

    @Throws(Exception::class)
    fun mockTrnGetResponse(portfolio: Portfolio?, trnFile: String) {
        val jsonFile = ClassPathResource(trnFile).file
        val trnResponse = objectMapper.readValue(jsonFile, TrnResponse::class.java)
        Mockito.`when`(
            trnService.findForPortfolio(
                portfolio!!,
                Objects.requireNonNull(dateUtils.date)
            )
        ).thenReturn(trnResponse)
    }

    @Throws(Exception::class)
    fun mockTrnPostResponse(portfolio: Portfolio?) {
        Mockito.`when`(
            trnService.save(
                portfolio!!,
                objectMapper.readValue(
                    ClassPathResource("contracts/trn/CSV-write.json").file, TrnRequest::class.java
                )
            )
        )
            .thenReturn(
                objectMapper.readValue(
                    ClassPathResource("contracts/trn/CSV-response.json").file, TrnResponse::class.java
                )
            )
    }

    @Throws(Exception::class)
    fun mockPortfolios() {
        mockPortfolio(emptyPortfolio)
        mockPortfolio(testPortfolio)
        // All Portfolio
        Mockito.`when`(portfolioService.portfolios).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/portfolio/portfolios.json").file,
                PortfoliosResponse::class.java
            ).data
        )
        Mockito.`when`(
            portfolioService.findWhereHeld(
                "KMI",
                dateUtils.getDate("2020-05-01", dateUtils.getZoneId())
            )
        )
            .thenReturn(
                objectMapper.readValue(
                    ClassPathResource("contracts/portfolio/where-held-response.json").file,
                    PortfoliosResponse::class.java
                )
            )
        Mockito.`when`(
            portfolioService.save(
                objectMapper.readValue(
                    ClassPathResource("contracts/portfolio/add-request.json")
                        .file,
                    PortfoliosRequest::class.java
                ).data
            )
        )
            .thenReturn(
                objectMapper.readValue(
                    ClassPathResource("contracts/portfolio/add-response.json")
                        .file,
                    PortfoliosResponse::class.java
                )
                    .data
            )
    }

    @get:Throws(IOException::class)
    private val testPortfolio: Portfolio
        get() {
            val jsonFile = ClassPathResource("contracts/portfolio/test.json").file
            return getPortfolio(jsonFile)
        }

    @get:Throws(IOException::class)
    private val emptyPortfolio: Portfolio
        get() {
            val jsonFile = ClassPathResource("contracts/portfolio/empty.json").file
            return getPortfolio(jsonFile)
        }

    @Throws(IOException::class)
    private fun getPortfolio(jsonFile: File): Portfolio {
        val (data) = objectMapper.readValue(jsonFile, PortfolioResponse::class.java)
        return data
    }

    private fun mockPortfolio(portfolio: Portfolio) {
        // For the sake of convenience when testing; id and code are the same
        Mockito.`when`(portfolioService.find(portfolio.id))
            .thenReturn(portfolio)
        Mockito.`when`(portfolioService.findByCode(portfolio.code))
            .thenReturn(portfolio)
    }

    @Throws(Exception::class)
    private fun mockAssets() {
        Mockito.`when`(assetService.find("KMI"))
            .thenReturn(
                objectMapper.readValue(
                    ClassPathResource("contracts/assets/kmi-asset-by-id.json").file,
                    AssetResponse::class.java
                ).data
            )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/request.json").file,
            ClassPathResource("contracts/assets/response.json").file
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/ebay-request.json").file,
            ClassPathResource("contracts/assets/ebay-response.json").file
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/msft-request.json").file,
            ClassPathResource("contracts/assets/msft-response.json").file
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/bhp-request.json").file,
            ClassPathResource("contracts/assets/bhp-response.json").file
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/bhp-lse-request.json").file,
            ClassPathResource("contracts/assets/bhp-lse-response.json").file
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/abbv-request.json").file,
            ClassPathResource("contracts/assets/abbv-response.json").file
        )
        mockAssetCreateResponses(
            ClassPathResource("contracts/assets/amp-request.json").file,
            ClassPathResource("contracts/assets/amp-response.json").file
        )
        val result: MutableMap<String, WtdMarketData> = HashMap()
        val ebayMd = WtdMarketData(
            BigDecimal("39.21"),
            BigDecimal("100.00"),
            BigDecimal("38.74"),
            BigDecimal("39.35"),
            Integer.decode("6274307")
        )
        result["EBAY"] = ebayMd
        val wtdResponse = WtdResponse("2019-10-18", result)
        Mockito.`when`(
            wtdGateway
                .getPrices("EBAY", "2019-10-18", "demo")
        )
            .thenReturn(wtdResponse)
    }

    @Throws(Exception::class)
    private fun mockAssetCreateResponses(jsonRequest: File, jsonResponse: File) {
        val assetRequest = objectMapper.readValue(jsonRequest, AssetRequest::class.java)
        val assetUpdateResponse = objectMapper.readValue(jsonResponse, AssetUpdateResponse::class.java)
        Mockito.`when`(assetService.process(assetRequest)).thenReturn(assetUpdateResponse)
        val keys = assetUpdateResponse.data.keys
        for (key in keys) {
            val theAsset = assetUpdateResponse.data[key]
            theAsset!!.id
            Mockito.`when`(assetService.find(theAsset.id)).thenReturn(theAsset)
            Mockito.`when`(
                assetService.findLocally(
                    theAsset.market.code.toUpperCase(),
                    theAsset.code.toUpperCase()
                )
            )
                .thenReturn(theAsset)
        }
    }

    private fun mockEcbRates(
        rates: Map<String, BigDecimal>,
        ecbRates: EcbRates,
        rateDate: String? = dateUtils.getDateString(ecbRates.date)
    ) {
        Mockito.`when`(
            fxGateway.getRatesForSymbols(
                rateDate, "USD",
                java.lang.String.join(",", rates.keys)
            )
        )
            .thenReturn(ecbRates)
    }

    @Test
    fun is_Started() {
        Assertions.assertThat(wtdGateway).isNotNull
        Assertions.assertThat(fxGateway).isNotNull
        Assertions.assertThat(assetService).isNotNull
        Assertions.assertThat(trnService).isNotNull
        Assertions.assertThat(portfolioService).isNotNull
    }

    companion object {
        val AAPL = getAsset("NASDAQ", "AAPL")
        val MSFT = getAsset("NASDAQ", "MSFT")
        val MSFT_INVALID = getAsset("NASDAQ", "MSFTx")
        val AMP = getAsset("ASX", "AMP")
    }
}
