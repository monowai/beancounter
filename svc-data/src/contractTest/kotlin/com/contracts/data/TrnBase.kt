package com.contracts.data

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.NoAuthConfig
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.trn.TrnQueryService
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.RegistrationUtils
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.util.Optional

const val asAtDate = "2021-10-18"

/**
 * Base class for Trn Contract tests. This is called by the spring cloud contract verifier
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK,
)
@WebAppConfiguration
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
class TrnBase {

    @Autowired
    lateinit var authConfig: NoAuthConfig

    @Autowired
    private lateinit var currencyService: CurrencyService

    @MockBean
    private lateinit var trnService: TrnService

    @MockBean
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var portfolioRepository: PortfolioRepository

    private lateinit var systemUser: SystemUser

    @Autowired
    internal lateinit var context: WebApplicationContext

    @MockBean
    internal lateinit var systemUserRepository: SystemUserRepository

    @MockBean
    internal lateinit var trnQueryService: TrnQueryService

    @MockBean
    internal lateinit var keyGenUtils: KeyGenUtils

    internal var dateUtils = DateUtils()

    @BeforeEach
    fun mockTrn() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        systemUser = ContractHelper.defaultUser(
            systemUserRepository = systemUserRepository,
            noAuthConfig = authConfig,
        )

        // This test depends on assets and portfolios being available
        AssetsBase().mockAssets(assetService)
        PortfolioBase.portfolios(systemUser, keyGenUtils, portfolioRepository)

        mockTrnPostResponse(PortfolioBase.testPortfolio)
        mockTrnGetResponse(PortfolioBase.testPortfolio, "contracts/trn/trns-test-response.json")
        mockTrnGetResponse(PortfolioBase.testPortfolio, "contracts/trn/trns-test-response.json", "2019-10-18")
        mockTrnGetResponse(PortfolioBase.emptyPortfolio, "contracts/trn/trns-empty-response.json")
        mockTrnGetResponse(
            cashPortfolio(),
            "contracts/trn/cash/ladder-response.json",
            asAtDate,
        )

        Mockito.`when`(
            trnQueryService.findAssetTrades(
                PortfolioBase.testPortfolio,
                "KMI",
                dateUtils.getDate("2020-05-01", dateUtils.getZoneId()),
            ),
        )
            .thenReturn(
                RegistrationUtils.objectMapper.readValue(
                    ClassPathResource("contracts/trn/trn-for-asset-response.json").file,
                    TrnResponse::class.java,
                ),
            )
    }

    private fun cashPortfolio(): Portfolio {
        val portfolio = Portfolio(
            id = "CASHLADDER",
            code = "CASHLADDER",
            name = "cashLadderFlow",
            currency = Constants.USD,
            base = Constants.NZD,
            owner = ContractHelper.getSystemUser(),
        )
        mockPortfolio(portfolio)
        return portfolio
    }

    private fun mockPortfolio(portfolio: Portfolio) {
        Mockito.`when`(portfolioRepository.findById(portfolio.id))
            .thenReturn(Optional.of(portfolio))
        Mockito.`when`(portfolioRepository.findByCodeAndOwner(portfolio.code, systemUser))
            .thenReturn(Optional.of(portfolio))
    }

    fun mockTrnGetResponse(portfolio: Portfolio, trnFile: String, date: String = dateUtils.today()) {
        val jsonFile = ClassPathResource(trnFile).file
        val trnResponse = RegistrationUtils.objectMapper.readValue(jsonFile, TrnResponse::class.java)
        Mockito.`when`(
            trnService.findForPortfolio(
                portfolio,
                dateUtils.getDate(date),
            ),
        ).thenReturn(trnResponse)
    }

    fun mockTrnPostResponse(portfolio: Portfolio) {
        Mockito.`when`(
            trnService.save(
                portfolio,
                RegistrationUtils.objectMapper.readValue(
                    ClassPathResource("contracts/trn/client-csv-request.json").file,
                    TrnRequest::class.java,
                ),
            ),
        ).thenReturn(
            RegistrationUtils.objectMapper.readValue(
                ClassPathResource("contracts/trn/client-csv-response.json").file,
                TrnResponse::class.java,
            ),
        )
    }
}
