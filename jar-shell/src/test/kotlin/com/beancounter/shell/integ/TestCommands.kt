package com.beancounter.shell.integ

import com.beancounter.auth.AutoConfigureMockAuth
import com.beancounter.auth.MockAuthConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson
import com.beancounter.shell.cli.DataCommands
import com.beancounter.shell.cli.EnvCommands
import com.beancounter.shell.cli.PortfolioCommands
import com.beancounter.shell.config.ShellConfig
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.shell.jline.PromptProvider
import org.springframework.test.context.ActiveProfiles

/**
 * Shell command unit tests.
 */
@ActiveProfiles("test")
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:+:stubs:10999"],
)
@SpringBootTest(classes = [ShellConfig::class, MockAuthConfig::class, ShareSightConfig::class])
@AutoConfigureMockAuth
class TestCommands {
    @Autowired
    private lateinit var dataCommands: DataCommands

    @Autowired
    private lateinit var envCommands: EnvCommands

    @Autowired
    private lateinit var portfolioCommands: PortfolioCommands

    @Autowired
    private lateinit var mockAuthConfig: MockAuthConfig

    @Autowired
    private lateinit var promptProvider: PromptProvider
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @BeforeEach
    fun mockLogin() {
        mockAuthConfig.login()
    }

    @Test
    fun is_MarketCommandsReturning() {
        var json = dataCommands.markets("NASDAQ")
        var marketResponse = objectMapper.readValue(json, MarketResponse::class.java)
        assertThat(marketResponse.data).isNotNull.hasSize(1)
        json = dataCommands.markets(null)
        marketResponse = objectMapper.readValue(json, MarketResponse::class.java)
        assertThat(marketResponse.data).isNotNull.hasSizeGreaterThan(3)
    }

    private val test = "TEST"

    @Test
    fun is_PortfolioByCode() {
        val json = portfolioCommands.portfolioCode(test)
        val portfolio = objectMapper.readValue(json, Portfolio::class.java)
        assertThat(portfolio).isNotNull
        assertThrows(BusinessException::class.java) {
            portfolioCommands.portfolioCode("is_PortfolioByCode")
        }
    }

    @Test
    fun is_PortfolioById() {
        val json = portfolioCommands.portfolio(test)
        val portfolio = objectMapper.readValue(json, Portfolio::class.java)
        assertThat(portfolio).isNotNull
        assertThrows(BusinessException::class.java) {
            portfolioCommands.portfolioCode("is_PortfolioById")
        }
    }

    @Test
    fun is_UtilCommands() {
        assertThat(envCommands.api()).isNotNull.isNotBlank
        assertThat(envCommands.pwd()).isNotNull.isNotBlank
    }

    @Test
    fun is_ConfigReturned() {
        val config = envCommands.config()
        assertThat(config).isNotNull
        val typeRef: TypeReference<HashMap<String, String>> = object : TypeReference<HashMap<String, String>>() {}
        val configMap: HashMap<String, String> = ObjectMapper().readValue(config, typeRef)
        assertThat(configMap).isNotEmpty
    }

    @Test
    fun is_PromptAvailable() {
        assertThat(promptProvider.prompt).isNotNull
    }
}
