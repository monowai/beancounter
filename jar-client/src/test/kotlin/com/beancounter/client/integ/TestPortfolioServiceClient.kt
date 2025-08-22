package com.beancounter.client.integ

import com.beancounter.auth.TokenService
import com.beancounter.client.Constants.Companion.SGD
import com.beancounter.client.Constants.Companion.USD
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.input.PortfolioInput
import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.ImportAutoConfiguration
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean

/**
 * Client side Portfolio tests using mock data from bc-data.
 */
@AutoConfigureStubRunner(
    failOnNoStubs = true,
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:0.1.1:stubs:10990"]
)
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
@ActiveProfiles("jar-client-shared", "contract-base")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TestPortfolioServiceClient {
    @Autowired
    private lateinit var portfolioService: PortfolioServiceClient

    @MockitoBean
    private lateinit var tokenService: TokenService

    @Test
    fun `should find portfolios`() {
        val portfolioByCode = portfolioService.getPortfolioByCode("TEST")
        val portfolioById =
            portfolioService.getPortfolioById(
                "TEST",
                "nothing"
            )
        assertThat(portfolioByCode).usingRecursiveComparison().isEqualTo(portfolioById)
    }

    @Test
    fun `should add portfolio request`() {
        val request =
            PortfoliosRequest(
                setOf(
                    PortfolioInput(
                        SGD.code,
                        "${SGD.code} Balanced",
                        USD.code,
                        SGD.code
                    )
                )
            )
        val response = portfolioService.add(request)
        assertThat(response).isNotNull.hasNoNullFieldsOrProperties()
        assertThat(response.data).isNotEmpty
    }

    @Test
    fun `should get my portfolios`() {
        val (data) = portfolioService.portfolios
        assertThat(data).isNotNull.isNotEmpty
    }

    @Test
    fun `should get where held`() {
        val (data) =
            portfolioService.getWhereHeld(
                "KMI",
                DateUtils().getFormattedDate("2020-05-01")
            )
        assertThat(data).isNotNull.isNotEmpty
    }

    @Test
    fun `should throw exception for illegal portfolio arguments`() {
        assertThrows(BusinessException::class.java) {
            portfolioService.getPortfolioByCode("NOT-FOUND")
        }
    }
}