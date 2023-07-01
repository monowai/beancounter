package com.beancounter.shell.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.PortfolioServiceClient.PortfolioGw
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.PortfolioResponse
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.shell.Constants.Companion.NZD
import com.beancounter.shell.Constants.Companion.USD
import com.beancounter.shell.commands.PortfolioCommands
import com.beancounter.shell.config.ShellConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cache.CacheManager
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.test.context.ActiveProfiles
import java.util.UUID

/**
 * Verify portfolio commands.
 */
@SpringBootTest(classes = [ShellConfig::class, ClientPasswordConfig::class, MockAuthConfig::class])
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"],
)
@ActiveProfiles("test")
@AutoConfigureMockAuth
class TestPortfolioCommands {
    @MockBean
    private lateinit var registrationService: RegistrationService

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var tokenService: TokenService

    private val bcJson = BcJson()

    @MockBean
    private lateinit var portfolioGw: PortfolioGw

    @MockBean
    private lateinit var cacheManager: CacheManager
    private lateinit var portfolioCommands: PortfolioCommands

    @Autowired
    fun initAuth() {
        portfolioCommands = PortfolioCommands(PortfolioServiceClient(portfolioGw, tokenService))
    }

    private val pfCode = "ABC"

    @BeforeEach
    fun autoLogin() {
        mockAuthConfig.login()
    }

    @Test
    fun createPortfolio() {
        val owner = systemUser
        val response = PortfoliosResponse(listOf(getPortfolio(pfCode, owner)))
        Mockito.`when`(
            portfolioGw.getPortfolioByCode(
                Mockito.anyString(),
                Mockito.anyString(),
            ),
        ).thenReturn(PortfolioResponse(getPortfolio(pfCode)))

        Mockito.`when`(
            portfolioGw.addPortfolios(
                Mockito.eq(tokenService.bearerToken),
                Mockito.isA(PortfoliosRequest::class.java),
            ),
        ).thenReturn(response)

        val result = portfolioCommands
            .add(pfCode, pfCode, NZD.code, USD.code)
        assertThat(result).isNotNull
        val portfolio = bcJson.objectMapper.readValue(result, Portfolio::class.java)
        assertThat(portfolio)
            .usingRecursiveComparison()
            .ignoringFields("owner")
            .isEqualTo(response.data.iterator().next())
    }

    @Test
    fun is_AddPortfolioThatExists() {
        val owner = systemUser
        val code = "ZZZ"
        val existing = getPortfolio(code, owner)
        val portfolioResponse = PortfolioResponse(existing)
        Mockito.`when`(portfolioGw.getPortfolioByCode(tokenService.bearerToken, existing.code))
            .thenReturn(portfolioResponse) // Portfolio exists
        val result = portfolioCommands
            .add(code, pfCode, NZD.code, USD.code)
        assertThat(result).isNotNull
        val portfolio = bcJson.objectMapper.readValue(result, Portfolio::class.java)
        assertThat(portfolio)
            .usingRecursiveComparison()
            .isEqualTo(portfolioResponse.data)
    }

    private val systemUser: SystemUser
        get() {
            return SystemUser(KeyGenUtils().format(UUID.randomUUID()))
        }

    private fun getPortfolio(code: String, owner: SystemUser): Portfolio {
        val toReturn = getPortfolio(code)
        toReturn.owner = owner
        return toReturn
    }
}
