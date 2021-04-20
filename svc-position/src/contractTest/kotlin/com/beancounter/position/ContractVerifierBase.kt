package com.beancounter.position

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.NZD
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.service.Valuation
import com.fasterxml.jackson.databind.ObjectMapper
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import org.mockito.Mockito.`when` as mockitoWhen

/**
 * Verifies that the data mocked in this service matches the contract definitions.
 */
@SpringBootTest(
    classes = [PositionBoot::class], properties = ["auth.enabled=false"],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@DirtiesContext
@WebAppConfiguration
class ContractVerifierBase {
    private val dateUtils = DateUtils()
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Autowired
    private lateinit var context: WebApplicationContext

    @MockBean
    private lateinit var valuationService: Valuation

    @MockBean
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @BeforeEach
    @Throws(Exception::class)
    fun initMocks() {
        val mockMvc = MockMvcBuilders
            .webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)
        mockApis()
    }

    fun mockApis() {
        val portfolioId = "TEST"
        val valuationDate = "2020-05-01"
        val testPortfolio = Portfolio(
            portfolioId,
            portfolioId,
            "${NZD.code} Portfolio",
            Currency(NZD.code),
            Currency(USD.code),
            null
        )

        mockitoWhen(portfolioServiceClient.getPortfolioByCode(portfolioId))
            .thenReturn(testPortfolio)

        mockitoWhen(portfolioServiceClient.getPortfolioById(portfolioId))
            .thenReturn(testPortfolio)

        mockitoWhen(
            valuationService.build(
                TrustedTrnQuery(
                    testPortfolio,
                    dateUtils.getDate(valuationDate), "KMI"
                )
            )
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/kmi-response.json").file,
                PositionResponse::class.java
            )
        )

        mockitoWhen(
            valuationService.build(
                TrustedTrnQuery(
                    testPortfolio,
                    dateUtils.getDate(valuationDate), "MSFT"
                )
            )
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/msft-response.json").file,
                PositionResponse::class.java
            )
        )

        mockitoWhen(
            valuationService.build(testPortfolio, valuationDate)
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/test-response.json").file,
                PositionResponse::class.java
            )
        )
    }
}
