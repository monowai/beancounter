package com.beancounter.client.integ

import com.beancounter.auth.TokenService
import com.beancounter.client.config.ClientConfig
import com.beancounter.client.services.StaticService
import com.beancounter.common.exception.BusinessException
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
 * Client side access to static data - Markets & Currencies.
 */
@AutoConfigureStubRunner(
    stubsMode = StubRunnerProperties.StubsMode.LOCAL,
    ids = ["org.beancounter:svc-data:0.1.1:stubs:10990"]
)
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
@ActiveProfiles("jar-client-shared", "contract-base")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StaticServiceTest {
    @Autowired
    private lateinit var staticService: StaticService

    @MockitoBean
    private lateinit var tokenService: TokenService

    @Test
    fun `should perform guard checks`() {
        assertThat(staticService.getCurrency(null)).isNull()
        assertThrows(BusinessException::class.java) { staticService.getCurrency("NOPE") }
    }

    @Test
    fun `should find markets`() {
        val markets = staticService.getMarkets()
        assertThat(markets).isNotNull
        assertThat(markets.data).isNotEmpty
    }

    @Test
    fun `should throw exception for illegal market arguments`() {
        assertThrows(BusinessException::class.java) { staticService.getMarket("ERR") }
    }

    @Test
    fun `should find currencies`() {
        val currencies = staticService.currencies
        assertThat(currencies).isNotNull
        assertThat(currencies.data).isNotEmpty
    }

    @Test
    fun `should find currency`() {
        val currency = staticService.getCurrency("USD")
        assertThat(currency).isNotNull
        assertThat(currency).hasNoNullFieldsOrProperties()
    }
}