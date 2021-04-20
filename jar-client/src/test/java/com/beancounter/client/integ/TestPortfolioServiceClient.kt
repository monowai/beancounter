package com.beancounter.client.integ

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
/**
 * Client side Portfolio tests using mock data from bc-data.
 */
@AutoConfigureStubRunner(
    failOnNoStubs = true,
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
class TestPortfolioServiceClient {
    @Autowired
    private lateinit var portfolioService: PortfolioServiceClient

    @Test
    fun is_PortfolioFinders() {
        val portfolioByCode = portfolioService.getPortfolioByCode("TEST")
        val portfolioById = portfolioService.getPortfolioById("TEST")
        assertThat(portfolioByCode).usingRecursiveComparison().isEqualTo(portfolioById)
    }

    @Test
    fun is_PortfolioAddRequest() {
        val request = PortfoliosRequest(
            setOf(
                PortfolioInput(SGD.code, "${SGD.code} Balanced", USD.code, SGD.code)
            )
        )
        val response = portfolioService.add(request)
        assertThat(response).isNotNull.hasNoNullFieldsOrProperties()
        assertThat(response.data).isNotEmpty
    }

    @Test
    fun is_MyPortfolios() {
        val (data) = portfolioService.portfolios
        assertThat(data).isNotNull.isNotEmpty
    }

    @Test
    fun is_WhereHeld() {
        val (data) = portfolioService.getWhereHeld(
            "KMI",
            DateUtils().getDate("2020-05-01")
        )
        assertThat(data).isNotNull.isNotEmpty
    }

    @Test
    fun is_PortfolioIllegalArgumentsThrowing() {
        assertThrows(BusinessException::class.java) {
            portfolioService.getPortfolioByCode("NOT-FOUND")
        }
    }
}
