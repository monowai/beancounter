package com.contracts.data

import com.beancounter.auth.AuthUtilService
import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.TokenService
import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.marketdata.Constants
import com.beancounter.marketdata.MarketDataBoot
import com.beancounter.marketdata.assets.AssetFinder
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.registration.SystemUserRepository
import com.beancounter.marketdata.registration.SystemUserService
import com.beancounter.marketdata.trn.TrnQueryService
import com.beancounter.marketdata.trn.TrnService
import com.fasterxml.jackson.module.kotlin.readValue
import io.restassured.RestAssured
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ClassPathResource
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.Optional

const val AS_AT_DATE = "2021-10-18"

/**
 * Base class for Trn Contract tests. This is called by the spring cloud contract verifier
 */
@SpringBootTest(
    classes = [MarketDataBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("contracts")
@AutoConfigureNoAuth
class TrnBase {
    @LocalServerPort
    lateinit var port: String

    @MockitoBean
    internal lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    internal lateinit var tokenService: TokenService

    @MockitoBean
    internal lateinit var systemUserRepository: SystemUserRepository

    @MockitoBean
    private lateinit var systemUserService: SystemUserService

    @MockitoBean
    private lateinit var trnService: TrnService

    @MockitoBean
    private lateinit var assetService: AssetService

    @MockitoBean
    private lateinit var assetFinder: AssetFinder

    @MockitoBean
    private lateinit var portfolioRepository: PortfolioRepository

    private var systemUser: SystemUser = ContractHelper.getSystemUser()

    @MockitoBean
    internal lateinit var trnQueryService: TrnQueryService

    @MockitoBean
    internal lateinit var keyGenUtils: KeyGenUtils

    @Autowired
    lateinit var authUtilService: AuthUtilService

    @Autowired
    lateinit var dateUtils: DateUtils

    @BeforeEach
    fun mockTrn() {
        RestAssured.port = Integer.valueOf(port)

        authUtilService.authenticate(
            ContractHelper.getSystemUser()
        )
        Mockito
            .`when`(systemUserService.getOrThrow())
            .thenReturn(ContractHelper.getSystemUser())

        // This test depends on assets and portfolios being available
        AssetsBase().mockAssets(assetService, assetFinder)
        PortfolioBase.portfolios(
            systemUser,
            keyGenUtils,
            portfolioRepository
        )

        mockTrnPostResponse(PortfolioBase.testPortfolio)
        mockTrnServiceResponse(
            PortfolioBase.testPortfolio,
            "contracts/trn/trns-test-response.json"
        )
        mockTrnServiceResponse(
            PortfolioBase.testPortfolio,
            "contracts/trn/trns-test-response.json",
            "2019-10-18"
        )
        mockTrnServiceResponse(
            PortfolioBase.emptyPortfolio,
            "contracts/trn/trns-empty-response.json"
        )
        mockTrnServiceResponse(
            cashPortfolio(),
            "contracts/trn/cash/ladder-response.json",
            AS_AT_DATE
        )

        Mockito
            .`when`(
                trnQueryService.findAssetTrades(
                    PortfolioBase.testPortfolio,
                    "KMI",
                    dateUtils.getDate("2020-05-01")
                )
            ).thenReturn(
                objectMapper
                    .readValue<TrnResponse>(
                        ClassPathResource("contracts/trn/trn-for-asset-response.json").file
                    ).data
            )
    }

    private fun cashPortfolio(): Portfolio {
        val id = "CASHLADDER"
        val portfolio =
            Portfolio(
                id = id,
                code = id,
                name = "cashLadderFlow",
                currency = Constants.USD,
                base = Constants.NZD,
                owner = ContractHelper.getSystemUser()
            )
        mockPortfolio(portfolio)
        return portfolio
    }

    private fun mockPortfolio(portfolio: Portfolio) {
        Mockito
            .`when`(portfolioRepository.findById(portfolio.id))
            .thenReturn(Optional.of(portfolio))
        Mockito
            .`when`(
                portfolioRepository.findByCodeAndOwner(
                    portfolio.code,
                    systemUser
                )
            ).thenReturn(Optional.of(portfolio))
    }

    fun mockTrnServiceResponse(
        portfolio: Portfolio,
        trnFile: String,
        date: String = "today"
    ) {
        val jsonFile = ClassPathResource(trnFile).file
        val trnResponse =
            objectMapper.readValue(
                jsonFile,
                TrnResponse::class.java
            )
        Mockito
            .`when`(
                trnService.findForPortfolio(
                    portfolio.id,
                    dateUtils.getFormattedDate(date)
                )
            ).thenReturn(trnResponse.data)
    }

    fun mockTrnPostResponse(portfolio: Portfolio) {
        Mockito
            .`when`(
                trnService.save(
                    portfolio.id,
                    objectMapper.readValue(
                        ClassPathResource("contracts/trn/client-csv-request.json").file,
                        TrnRequest::class.java
                    )
                )
            ).thenReturn(
                objectMapper
                    .readValue<TrnResponse>(
                        ClassPathResource("contracts/trn/client-csv-response.json").file
                    ).data
            )
    }
}