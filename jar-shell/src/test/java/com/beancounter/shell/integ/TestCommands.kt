package com.beancounter.shell.integ

import com.beancounter.auth.client.AuthClientConfig
import com.beancounter.client.sharesight.ShareSightConfig
import com.beancounter.common.contracts.MarketResponse
import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.BcJson
import com.beancounter.shell.cli.DataCommands
import com.beancounter.shell.cli.PortfolioCommands
import com.beancounter.shell.cli.UtilCommands
import com.beancounter.shell.config.ShellConfig
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assert.assertThrows
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties
import org.springframework.shell.jline.PromptProvider
import org.springframework.test.context.ActiveProfiles

@ActiveProfiles("test")
@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.LOCAL, ids = ["org.beancounter:svc-data:+:stubs:10999"])
@SpringBootTest(classes = [ShellConfig::class, AuthClientConfig::class, ShareSightConfig::class])
class TestCommands {
    @Autowired
    private lateinit var dataCommands: DataCommands

    @Autowired
    private lateinit var utilCommands: UtilCommands

    @Autowired
    private lateinit var portfolioCommands: PortfolioCommands

    @Autowired
    private val promptProvider: PromptProvider? = null
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    @Test
    @Throws(Exception::class)
    fun is_MarketCommandsReturning() {
        var json = dataCommands.markets("NASDAQ")
        var marketResponse = objectMapper.readValue(json, MarketResponse::class.java)
        assertThat(marketResponse.data).isNotNull.hasSize(1)
        json = dataCommands.markets(null)
        marketResponse = objectMapper.readValue(json, MarketResponse::class.java)
        assertThat(marketResponse.data).isNotNull.hasSizeGreaterThan(3)
    }

    @Test
    @Throws(Exception::class)
    fun is_PortfolioByCode() {
        val json = portfolioCommands.code("TEST")
        val portfolio = objectMapper.readValue(json, Portfolio::class.java)
        assertThat(portfolio).isNotNull
        assertThrows(BusinessException::class.java) { portfolioCommands.code("ILLEGAL") }
    }

    @Test
    @Throws(Exception::class)
    fun is_PortfolioById() {
        val json = portfolioCommands.id("TEST")
        val portfolio = objectMapper.readValue(json, Portfolio::class.java)
        assertThat(portfolio).isNotNull
        assertThrows(BusinessException::class.java) { portfolioCommands.code("ILLEGAL") }
    }

    @Test
    fun is_UtilCommands() {
        assertThat(utilCommands.api()).isNotNull.isNotBlank
        assertThat(utilCommands.pwd()).isNotNull.isNotBlank
    }

    @Test
    fun is_ConfigReturned() {
        val config = utilCommands.config()
        assertThat(config).isNotNull
        val typeRef: TypeReference<HashMap<String?, String?>?> = object : TypeReference<HashMap<String?, String?>?>() {}
        val configMap: HashMap<String?, String?>? = ObjectMapper().readValue(config, typeRef)
        assertThat(configMap).isNotEmpty
    }

    @Test
    fun is_PromptAvailable() {
        assertThat(promptProvider).isNotNull
        assertThat(promptProvider!!.prompt).isNotNull
    }
}
