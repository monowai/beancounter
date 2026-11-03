package com.beancounter.marketdata.config

import com.beancounter.marketdata.cash.CashService
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mockito
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.junit.jupiter.SpringExtension

/**
 * Test suite for StaticConfigService to ensure proper application startup configuration.
 *
 * This class tests:
 * - Application context refresh event handling
 * - Currency service persistence on startup
 * - Cash balance asset creation on startup
 * - Service initialization and dependency injection
 *
 * Tests verify that the StaticConfigService correctly initializes
 * required services when the application context is refreshed.
 */
@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [StaticConfigService::class])
class StaticConfigServiceTest {
    @Autowired
    private lateinit var applicationContext: ApplicationContext

    @MockitoBean
    private lateinit var currencyService: CurrencyService

    @MockitoBean
    private lateinit var cashService: CashService

    @Test
    fun `should load static config when the context refreshes`() {
        // The listener takes no event argument; Spring still dispatches the
        // ContextRefreshedEvent it declares, so starting the context runs it.
        verify(currencyService, Mockito.atLeastOnce()).persist()
        verify(cashService, Mockito.atLeastOnce()).createCashBalanceAssets()
    }

    @Test
    fun `should have static config service available in application context`() {
        // When checking if the static config service exists
        val staticConfigService = applicationContext.getBean(StaticConfigService::class.java)

        // Then the service should be available
        assertThat(staticConfigService).isNotNull()
        assertThat(staticConfigService.currencyService).isNotNull()
        assertThat(staticConfigService.cashService).isNotNull()
    }

    @Test
    fun `should have dependencies injected correctly`() {
        // Given the static config service
        val staticConfigService = applicationContext.getBean(StaticConfigService::class.java)

        // Then the dependencies should be injected
        assertThat(staticConfigService.currencyService).isSameAs(currencyService)
        assertThat(staticConfigService.cashService).isSameAs(cashService)
    }
}