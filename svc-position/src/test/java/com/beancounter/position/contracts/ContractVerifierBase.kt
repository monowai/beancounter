package com.beancounter.position.contracts

import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PositionResponse
import com.beancounter.common.input.TrustedTrnQuery
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.common.utils.CurrencyUtils
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.PositionBoot
import com.beancounter.position.service.Valuation
import io.restassured.module.mockmvc.RestAssuredMockMvc
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.verifier.messaging.boot.AutoConfigureMessageVerifier
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock
import org.springframework.core.io.ClassPathResource
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.web.WebAppConfiguration
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest(classes = [PositionBoot::class], properties = ["auth.enabled=false"], webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@DirtiesContext
@AutoConfigureMessageVerifier
@AutoConfigureWireMock(port = 0)
@WebAppConfiguration
class ContractVerifierBase {
    private val dateUtils = DateUtils()
    private val currencyUtils = CurrencyUtils()

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
        val testPortfolio = Portfolio(
                "TEST",
                "TEST",
                "NZD Portfolio",
                currencyUtils.getCurrency("NZD"),
                currencyUtils.getCurrency("USD"),
                null)
        Mockito.`when`(portfolioServiceClient.getPortfolioByCode("TEST"))
                .thenReturn(testPortfolio)
        Mockito.`when`(portfolioServiceClient.getPortfolioById("TEST"))
                .thenReturn(testPortfolio)
        Mockito.`when`(
                valuationService.build(
                        TrustedTrnQuery(testPortfolio,
                                dateUtils.getDate("2020-05-01")!!, "KMI")))
                .thenReturn(objectMapper.readValue(
                        ClassPathResource("contracts/kmi-response.json").file,
                        PositionResponse::class.java))
        Mockito.`when`(
                valuationService.build(
                        TrustedTrnQuery(testPortfolio,
                                dateUtils.getDate("2020-05-01")!!, "MSFT")))
                .thenReturn(objectMapper.readValue(
                        ClassPathResource("contracts/msft-response.json").file,
                        PositionResponse::class.java))
        Mockito.`when`(valuationService.build(testPortfolio, "2020-05-01"))
                .thenReturn(objectMapper.readValue(
                        ClassPathResource("contracts/test-response.json").file,
                        PositionResponse::class.java))
    }

    @Test
    fun is_Started() {
        assertThat(valuationService).isNotNull()
    }
}