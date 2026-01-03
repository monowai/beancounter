package com.beancounter.marketdata.fx

import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.FrankfurterGateway
import com.beancounter.marketdata.fx.fxrates.FrankfurterResponse
import com.beancounter.marketdata.fx.fxrates.FrankfurterService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for FrankfurterService.
 */
class FrankfurterServiceTest {
    private val frankfurterGateway = mock(FrankfurterGateway::class.java)
    private val currencyService = mock(CurrencyService::class.java)
    private val dateUtils = DateUtils()

    @Test
    fun `should return FX rates from Frankfurter`() {
        val testDate = "2024-01-15"
        val baseCurrency =
            com.beancounter.common.model
                .Currency("USD")
        val audCurrency =
            com.beancounter.common.model
                .Currency("AUD")
        val eurCurrency =
            com.beancounter.common.model
                .Currency("EUR")

        val currencyConfig =
            mock(com.beancounter.marketdata.currency.CurrencyConfig::class.java)
        `when`(currencyConfig.baseCurrency).thenReturn(baseCurrency)
        `when`(currencyService.currencyConfig).thenReturn(currencyConfig)
        `when`(currencyService.currenciesAs()).thenReturn("AUD,EUR")
        `when`(currencyService.getCode("AUD")).thenReturn(audCurrency)
        `when`(currencyService.getCode("EUR")).thenReturn(eurCurrency)

        `when`(
            frankfurterGateway.getRatesForSymbols(
                any(),
                any(),
                any()
            )
        ).thenReturn(
            FrankfurterResponse(
                base = "USD",
                date = LocalDate.parse(testDate),
                rates =
                    mapOf(
                        "AUD" to BigDecimal("1.5364"),
                        "EUR" to BigDecimal("0.9123")
                    )
            )
        )

        val service = FrankfurterService(frankfurterGateway, currencyService, dateUtils)
        val rates = service.getRates(testDate)

        assertThat(rates).hasSize(2)
        assertThat(rates.map { it.to.code }).containsExactlyInAnyOrder("AUD", "EUR")
    }

    @Test
    fun `should return empty list when Frankfurter fails`() {
        val currencyConfig =
            mock(com.beancounter.marketdata.currency.CurrencyConfig::class.java)
        val baseCurrency =
            com.beancounter.common.model
                .Currency("USD")
        `when`(currencyConfig.baseCurrency).thenReturn(baseCurrency)
        `when`(currencyService.currencyConfig).thenReturn(currencyConfig)
        `when`(currencyService.currenciesAs()).thenReturn("AUD,EUR")

        `when`(
            frankfurterGateway.getRatesForSymbols(
                any(),
                any(),
                any()
            )
        ).thenThrow(RuntimeException("Connection failed"))

        val service = FrankfurterService(frankfurterGateway, currencyService, dateUtils)
        val rates = service.getRates("2024-01-15")

        assertThat(rates).isEmpty()
    }

    @Test
    fun `should have correct provider id`() {
        val service = FrankfurterService(frankfurterGateway, currencyService, dateUtils)
        assertThat(service.id).isEqualTo("FRANKFURTER")
    }
}