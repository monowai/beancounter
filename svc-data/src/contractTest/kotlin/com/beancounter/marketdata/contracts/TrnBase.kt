package com.beancounter.marketdata.contracts

import com.beancounter.common.contracts.TrnRequest
import com.beancounter.common.contracts.TrnResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.marketdata.assets.AssetService
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.portfolio.PortfolioRepository
import com.beancounter.marketdata.trn.TrnService
import com.beancounter.marketdata.utils.RegistrationUtils.objectMapper
import contracts.ContractHelper
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.test.web.servlet.setup.MockMvcBuilders

/**
 * Base class for Trn Contract tests. This is called by the spring cloud contract verifier
 */
class TrnBase : ContractVerifierBase() {
    @Autowired
    private lateinit var currencyService: CurrencyService

    @MockBean
    private lateinit var trnService: TrnService

    @MockBean
    private lateinit var assetService: AssetService

    @MockBean
    private lateinit var portfolioRepository: PortfolioRepository

    private lateinit var systemUser: SystemUser

    @BeforeEach
    fun mock() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        systemUser = ContractHelper.defaultUser(
            jwtDecoder = jwtDecoder,
            tokenService = tokenService,
            systemUserRepository = systemUserRepository
        )

        // This test depends on assets and portfolios being available
        AssetsBase().mockAssets(assetService)
        PortfolioBase.portfolios(systemUser, keyGenUtils, portfolioRepository)
        // We are testing this
        mockPortfolioTrns()
    }

    fun mockPortfolioTrns() {
        mockTrnPostResponse(PortfolioBase.testPortfolio)
        mockTrnGetResponse(PortfolioBase.testPortfolio, "contracts/trn/trns-test-response.json")
        mockTrnGetResponse(PortfolioBase.emptyPortfolio, "contracts/trn/trns-empty-response.json")
        Mockito.`when`(
            trnService.findByPortfolioAsset(
                PortfolioBase.testPortfolio,
                "KMI",
                dateUtils.getDate("2020-05-01", dateUtils.getZoneId())
            )
        )
            .thenReturn(
                objectMapper.readValue(
                    ClassPathResource("contracts/trn/trn-for-asset-response.json").file,
                    TrnResponse::class.java
                )
            )
    }

    @Throws(Exception::class)
    fun mockTrnGetResponse(portfolio: Portfolio, trnFile: String) {
        val jsonFile = ClassPathResource(trnFile).file
        val trnResponse = objectMapper.readValue(jsonFile, TrnResponse::class.java)
        Mockito.`when`(
            trnService.findForPortfolio(
                portfolio,
                dateUtils.date
            )
        ).thenReturn(trnResponse)
    }

    @Throws(Exception::class)
    fun mockTrnPostResponse(portfolio: Portfolio?) {
        Mockito.`when`(
            trnService.save(
                portfolio!!,
                objectMapper.readValue(
                    ClassPathResource("contracts/trn/CSV-request.json").file, TrnRequest::class.java
                )
            )
        )
            .thenReturn(
                objectMapper.readValue(
                    ClassPathResource("contracts/trn/CSV-response.json").file, TrnResponse::class.java
                )
            )
    }
}
