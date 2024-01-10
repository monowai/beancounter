package com.contracts.position

import com.beancounter.auth.AutoConfigureNoAuth
import com.beancounter.auth.TokenService
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
import io.restassured.RestAssured
import org.junit.jupiter.api.BeforeEach
import org.mockito.Mockito.`when`
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.core.io.ClassPathResource
import org.springframework.web.context.WebApplicationContext

private const val BEARER_TOKEN = "no-token"

/**
 * Verifies that the data mocked in this service matches the contract definitions.
 */
@SpringBootTest(
    classes = [PositionBoot::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@AutoConfigureNoAuth
class ContractVerifierBase {
    private val dateUtils = DateUtils()
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @MockBean
    private lateinit var valuationService: Valuation

    @Autowired
    private lateinit var tokenService: TokenService

    @MockBean
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Autowired
    private lateinit var context: WebApplicationContext

    @LocalServerPort
    lateinit var port: String

    @BeforeEach
    fun initMocks() {
        RestAssured.port = Integer.valueOf(port)
        val portfolioId = "TEST"
        val valuationDate = "2020-05-01"
        val testPortfolio =
            Portfolio(
                portfolioId,
                portfolioId,
                "${Constants.NZD.code} Portfolio",
                Currency(Constants.NZD.code),
                Currency(Constants.USD.code),
            )

        `when`(portfolioServiceClient.getPortfolioByCode(portfolioId))
            .thenReturn(testPortfolio)

        `when`(portfolioServiceClient.getPortfolioById(portfolioId))
            .thenReturn(testPortfolio)

        `when`(portfolioServiceClient.getPortfolioById(portfolioId, BEARER_TOKEN))
            .thenReturn(testPortfolio)

        `when`(
            valuationService.build(
                TrustedTrnQuery(
                    testPortfolio,
                    dateUtils.getDate(valuationDate),
                    "KMI",
                ),
            ),
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/kmi-response.json").file,
                PositionResponse::class.java,
            ),
        )

        `when`(
            valuationService.build(
                TrustedTrnQuery(
                    testPortfolio,
                    dateUtils.getDate(valuationDate),
                    "MSFT",
                ),
            ),
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/msft-response.json").file,
                PositionResponse::class.java,
            ),
        )

        `when`(
            valuationService.getPositions(testPortfolio, valuationDate, true),
        ).thenReturn(
            objectMapper.readValue(
                ClassPathResource("contracts/test-response.json").file,
                PositionResponse::class.java,
            ),
        )
    }
}
