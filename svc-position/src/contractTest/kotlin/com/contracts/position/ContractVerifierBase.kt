package com.contracts.position

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants
import com.beancounter.position.PositionBoot
import com.beancounter.position.valuation.Valuation
import com.fasterxml.jackson.databind.ObjectMapper
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.core.io.ClassPathResource
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

/**
 * Verifies that the data mocked in this service matches the contract definitions.
 */
@SpringBootTest(
    classes = [PositionBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.MOCK
)
@AutoConfigureNoAuth
@WebAppConfiguration
class ContractVerifierBase {
    private val dateUtils = DateUtils()
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @MockBean
    private lateinit var valuationService: Valuation

    @MockBean
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Autowired
    private lateinit var context: WebApplicationContext

    @BeforeEach
    fun initMocks() {
        val mockMvc = MockMvcBuilders.webAppContextSetup(context)
            .build()
        RestAssuredMockMvc.mockMvc(mockMvc)

        val portfolioId = "TEST"
        val valuationDate = "2020-05-01"
        val testPortfolio = Portfolio(
            portfolioId,
            portfolioId,
            "${Constants.NZD.code} Portfolio",
            Currency(Constants.NZD.code),
            Currency(Constants.USD.code),
            null
        )

        Mockito.`when`(portfolioServiceClient.getPortfolioByCode(portfolioId))
            .thenReturn(testPortfolio)

        Mockito.`when`(portfolioServiceClient.getPortfolioById(portfolioId))
            .thenReturn(testPortfolio)

        Mockito.`when`(
            valuationService.build(
                TrustedTrnQuery(
                    testPortfolio,
                    dateUtils.getDate(valuationDate),
                    "KMI"
                )
            )
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/kmi-response.json").file,
                PositionResponse::class.java
            )
        )

        Mockito.`when`(
            valuationService.build(
                TrustedTrnQuery(
                    testPortfolio,
                    dateUtils.getDate(valuationDate),
                    "MSFT"
                )
            )
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/msft-response.json").file,
                PositionResponse::class.java
            )
        )

        Mockito.`when`(
            valuationService.getPositions(testPortfolio, valuationDate, true)
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/test-response.json").file,
                PositionResponse::class.java
            )
        )
    }
}
