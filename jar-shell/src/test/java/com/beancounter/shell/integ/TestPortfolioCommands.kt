package com.beancounter.shell.integ

import com.beancounter.auth.client.AuthClientConfig
import com.beancounter.auth.common.TokenService
import com.beancounter.auth.common.TokenUtils
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
import com.beancounter.shell.cli.PortfolioCommands
import com.beancounter.shell.config.ShellConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import kotlin.collections.ArrayList

/**
 * Verify portfolio commands.
 */
@SpringBootTest(classes = [ShellConfig::class, AuthClientConfig::class])
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"]
)
@ActiveProfiles("test")
class TestPortfolioCommands {
    @MockBean
    private lateinit var registrationService: RegistrationService

    @MockBean
    private lateinit var jwtDecoder: JwtDecoder
    private val bcJson = BcJson()

    private val tokenService = TokenService()
    private var portfolioGw: PortfolioGw = Mockito.mock(PortfolioGw::class.java)
    private var portfolioCommands: PortfolioCommands =
        PortfolioCommands(PortfolioServiceClient(portfolioGw, tokenService))

    @get:Test
    val portfolios: Unit
        get() {
            systemUser
            Mockito.`when`(portfolioGw.getPortfolios(Mockito.anyString()))
                .thenReturn(PortfoliosResponse(ArrayList()))
            val result = portfolioCommands.portfolios()
            assertThat(result).isNotBlank
        }

    @Test
    fun createPortfolio() {
        val owner = systemUser
        val response = PortfoliosResponse(listOf(getPortfolio("ABC", owner)))
        Mockito.`when`(
            portfolioGw.getPortfolioByCode(
                Mockito.anyString(),
                Mockito.anyString()
            )
        ).thenReturn(PortfolioResponse(getPortfolio("ABC")))

        Mockito.`when`(
            portfolioGw.addPortfolios(
                Mockito.eq(tokenService.bearerToken),
                Mockito.isA(PortfoliosRequest::class.java)
            )
        ).thenReturn(response)

        val result = portfolioCommands
            .add("ABC", "ABC", "NZD", "USD")
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
        val existing = getPortfolio("ZZZ", owner)
        val portfolioResponse = PortfolioResponse(existing)
        Mockito.`when`(portfolioGw.getPortfolioByCode(tokenService.bearerToken, existing.code))
            .thenReturn(portfolioResponse) // Portfolio exists
        val result = portfolioCommands
            .add("ZZZ", "ABC", "NZD", "USD")
        assertThat(result).isNotNull
        val portfolio = bcJson.objectMapper.readValue(result, Portfolio::class.java)
        assertThat(portfolio)
            .usingRecursiveComparison()
            .isEqualTo(portfolioResponse.data)
    }

    private val systemUser: SystemUser
        get() {
            val owner = SystemUser(KeyGenUtils().format(UUID.randomUUID()))
            val jwt = TokenUtils().getUserToken(owner)
            Mockito.`when`(jwtDecoder.decode("token")).thenReturn(jwt)
            Mockito.`when`(registrationService.me()).thenReturn(owner)
            SecurityContextHolder.getContext().authentication =
                JwtAuthenticationToken(jwtDecoder.decode("token"))
            return owner
        }

    private fun getPortfolio(code: String, owner: SystemUser): Portfolio {
        val toReturn = getPortfolio(code)
        toReturn.owner = owner
        return toReturn
    }
}
