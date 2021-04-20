package com.beancounter.client.integ

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

/**
 * Client side access to static data - Markets & Currencies.
 */
@AutoConfigureStubRunner(stubsMode = StubRunnerProperties.StubsMode.LOCAL, ids = ["org.beancounter:svc-data:+:stubs:10999"])
@ImportAutoConfiguration(ClientConfig::class)
@SpringBootTest(classes = [ClientConfig::class])
class StaticServiceTest {
    @Autowired
    private lateinit var staticService: StaticService

    @Test
    fun is_GuardChecks() {
        assertThat(staticService.getCurrency(null)).isNull()
        assertThrows(BusinessException::class.java) { staticService.getCurrency("NOPE") }
    }

    @Test
    fun are_MarketsFound() {
        val markets = staticService.getMarkets()
        assertThat(markets).isNotNull
        assertThat(markets.data).isNotEmpty
    }

    @Test
    fun is_MarketIllegalArgumentsThrowing() {
        assertThrows(BusinessException::class.java) { staticService.getMarket("ERR") }
    }

    @Test
    fun are_CurrenciesFound() {
        val currencies = staticService.currencies
        assertThat(currencies).isNotNull
        assertThat(currencies.data).isNotEmpty
    }

    @Test
    fun is_CurrencyFound() {
        val currency = staticService.getCurrency("USD")
        assertThat(currency).isNotNull
        assertThat(currency).hasNoNullFieldsOrProperties()
    }
}
