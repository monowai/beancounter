package com.beancounter.shell.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.auth.TokenService
import com.beancounter.auth.client.ClientPasswordConfig
import com.beancounter.client.services.PortfolioServiceClient
import com.beancounter.client.services.RegistrationService
import com.beancounter.common.contracts.PortfoliosRequest
import com.beancounter.common.contracts.PortfoliosResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.KeyGenUtils
import com.beancounter.common.utils.PortfolioUtils.Companion.getPortfolio
import com.beancounter.shell.Constants.Companion.NZD
import com.beancounter.shell.Constants.Companion.USD
import com.beancounter.shell.commands.PortfolioCommands
import com.beancounter.shell.config.ShellConfig
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.bean.override.mockito.MockitoBean
import java.util.UUID

/**
 * Verify portfolio commands.
 */
@SpringBootTest(classes = [ShellConfig::class, ClientPasswordConfig::class])
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:0.1.1:stubs:10991"]
)
@ActiveProfiles("jar-shell-shared", "contract-base")
@AutoConfigureMockAuth
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TestPortfolioCommands {
    @MockitoBean
    private lateinit var registrationService: RegistrationService

    @MockitoBean
    private lateinit var jwtDecoder: JwtDecoder

    @MockitoBean
    private lateinit var portfolioServiceClient: PortfolioServiceClient

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var tokenService: TokenService

    private lateinit var portfolioCommands: PortfolioCommands

    @BeforeEach
    fun configure() {
        portfolioCommands = PortfolioCommands(portfolioServiceClient)
    }

    private val pfCode = "ABC"

    @BeforeEach
    fun autoLogin() {
        mockAuthConfig.login()
    }

    @Test
    fun createPortfolio() {
        val owner = systemUser
        val response =
            PortfoliosResponse(
                listOf(
                    getPortfolio(
                        pfCode,
                        owner
                    )
                )
            )
        // Throw exception so creation path is taken
        `when`(portfolioServiceClient.getPortfolioByCode(anyString()))
            .thenThrow(BusinessException("Not found"))

        `when`(portfolioServiceClient.add(any<PortfoliosRequest>()))
            .thenReturn(response)

        val result =
            portfolioCommands.add(
                pfCode,
                pfCode,
                NZD.code,
                USD.code
            )
        assertThat(result).isNotNull
        val portfolio =
            objectMapper.readValue(
                result,
                Portfolio::class.java
            )
        assertThat(portfolio)
            .usingRecursiveComparison()
            .ignoringFields("owner")
            .isEqualTo(response.data.iterator().next())
    }

    @Test
    fun is_AddPortfolioThatExists() {
        val owner = systemUser
        val code = "ZZZ"
        val existing =
            getPortfolio(
                code,
                owner
            )
        `when`(portfolioServiceClient.getPortfolioByCode(existing.code))
            .thenReturn(existing) // Portfolio exists
        val result =
            portfolioCommands.add(
                code,
                pfCode,
                NZD.code,
                USD.code
            )
        assertThat(result).isNotNull
        val portfolio =
            objectMapper.readValue(
                result,
                Portfolio::class.java
            )
        assertThat(portfolio).usingRecursiveComparison().isEqualTo(existing)
    }

    private val systemUser: SystemUser
        get() {
            return SystemUser(KeyGenUtils().format(UUID.randomUUID()))
        }

    private fun getPortfolio(
        code: String,
        owner: SystemUser
    ): Portfolio {
        val toReturn = getPortfolio(code)
        toReturn.owner = owner
        return toReturn
    }
}