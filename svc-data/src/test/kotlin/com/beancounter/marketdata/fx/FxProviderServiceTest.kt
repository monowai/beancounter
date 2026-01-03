package com.beancounter.marketdata.fx

import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.fx.fxrates.FxRateProvider
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for FxProviderService - the composite provider with fallback.
 */
class FxProviderServiceTest {
    private val frankfurterProvider = mock(FxRateProvider::class.java)
    private val ecbProvider = mock(FxRateProvider::class.java)

    private val testDate = "2024-01-15"
    private val usd = Currency("USD")
    private val aud = Currency("AUD")

    private val sampleRates =
        listOf(
            FxRate(
                from = usd,
                to = aud,
                rate = BigDecimal("1.5364"),
                date = LocalDate.parse(testDate),
                provider = "FRANKFURTER"
            )
        )

    @Test
    fun `should use primary provider when available`() {
        `when`(frankfurterProvider.id).thenReturn("FRANKFURTER")
        `when`(ecbProvider.id).thenReturn("EXCHANGE_RATES_API")
        `when`(frankfurterProvider.getRates(testDate)).thenReturn(sampleRates)

        val service = FxProviderService(frankfurterProvider, ecbProvider, "FRANKFURTER")
        val rates = service.getRates(testDate)

        assertThat(rates).hasSize(1)
        assertThat(rates[0].to.code).isEqualTo("AUD")
        verify(ecbProvider, never()).getRates(testDate)
    }

    @Test
    fun `should fallback when primary returns empty`() {
        `when`(frankfurterProvider.id).thenReturn("FRANKFURTER")
        `when`(frankfurterProvider.getRates(testDate)).thenReturn(emptyList())
        `when`(ecbProvider.id).thenReturn("EXCHANGE_RATES_API")
        `when`(ecbProvider.getRates(testDate)).thenReturn(sampleRates)

        val service = FxProviderService(frankfurterProvider, ecbProvider, "FRANKFURTER")
        val rates = service.getRates(testDate)

        assertThat(rates).hasSize(1)
        verify(frankfurterProvider).getRates(testDate)
        verify(ecbProvider).getRates(testDate)
    }

    @Test
    fun `should return empty when both providers fail`() {
        `when`(frankfurterProvider.id).thenReturn("FRANKFURTER")
        `when`(frankfurterProvider.getRates(testDate)).thenReturn(emptyList())
        `when`(ecbProvider.id).thenReturn("EXCHANGE_RATES_API")
        `when`(ecbProvider.getRates(testDate)).thenReturn(emptyList())

        val service = FxProviderService(frankfurterProvider, ecbProvider, "FRANKFURTER")
        val rates = service.getRates(testDate)

        assertThat(rates).isEmpty()
        verify(frankfurterProvider).getRates(testDate)
        verify(ecbProvider).getRates(testDate)
    }

    @Test
    fun `should have correct provider id`() {
        `when`(frankfurterProvider.id).thenReturn("FRANKFURTER")
        `when`(ecbProvider.id).thenReturn("EXCHANGE_RATES_API")
        val service = FxProviderService(frankfurterProvider, ecbProvider, "FRANKFURTER")
        assertThat(service.id).isEqualTo("COMPOSITE")
    }

    @Test
    fun `should return default provider id from config`() {
        `when`(frankfurterProvider.id).thenReturn("FRANKFURTER")
        `when`(ecbProvider.id).thenReturn("EXCHANGE_RATES_API")
        val service = FxProviderService(frankfurterProvider, ecbProvider, "EXCHANGE_RATES_API")
        assertThat(service.getDefaultProviderId()).isEqualTo("EXCHANGE_RATES_API")
    }
}