package com.beancounter.marketdata.fx

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.AUD
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyConfig
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Tests that FxRateService falls back to the most recent cached rates
 * when both FX providers are unavailable.
 */
class FxRateFallbackTest {
    private lateinit var fxRateService: FxRateService
    private lateinit var fxRateRepository: FxRateRepository
    private val fxProviderService = mock(FxProviderService::class.java)
    private val currencyService = mock(CurrencyService::class.java)
    private val marketService = mock(MarketService::class.java)

    // Monday request, Friday's cached rates
    private val mondayDate = LocalDate.of(2024, 1, 22)
    private val fridayDate = LocalDate.of(2024, 1, 19)
    private val mondayString = "2024-01-22"

    private val fridayRates =
        listOf(
            FxRate(
                from = USD,
                to = AUD,
                rate = BigDecimal("1.53"),
                date = fridayDate,
                provider = "FRANKFURTER"
            ),
            FxRate(
                from = USD,
                to = NZD,
                rate = BigDecimal("1.62"),
                date = fridayDate,
                provider = "FRANKFURTER"
            )
        )

    @BeforeEach
    fun setUp() {
        fxRateRepository = mock(FxRateRepository::class.java)
        val currencyConfig = CurrencyConfig()
        currencyConfig.baseCurrency = USD
        `when`(currencyService.currencyConfig).thenReturn(currencyConfig)
        `when`(currencyService.getCode("NZD")).thenReturn(NZD)
        `when`(currencyService.getCode("USD")).thenReturn(USD)
        `when`(marketService.getMarket("US")).thenReturn(Market("US"))

        fxRateService =
            FxRateService(
                fxProviderService = fxProviderService,
                currencyService = currencyService,
                marketService = marketService,
                marketUtils = PreviousClosePriceDate(DateUtils()),
                fxRateRepository = fxRateRepository
            )
    }

    @Test
    fun `should fall back to cached rates when providers fail`() {
        // No cached rates for Monday
        `when`(fxRateRepository.findByDateRange(mondayDate)).thenReturn(emptyList())
        // Both providers return nothing
        `when`(fxProviderService.getRates(mondayString, null)).thenReturn(emptyList())
        `when`(fxProviderService.getDefaultProviderId()).thenReturn("FRANKFURTER")
        // Friday's rates exist in cache
        `when`(fxRateRepository.findMostRecentBefore(mondayDate)).thenReturn(fridayRates)
        // Base rate lookup
        `when`(fxRateRepository.findBaseRate(USD)).thenReturn(
            FxRate(
                from = USD,
                to = USD,
                rate = BigDecimal.ONE,
                date = LocalDate.of(1900, 1, 1)
            )
        )

        val request =
            FxRequest(
                rateDate = mondayString,
                pairs = mutableSetOf(IsoCurrencyPair("NZD", "USD"))
            )

        val response = fxRateService.getRates(request, "test-token")

        assertThat(response.data.rates).isNotEmpty
        val nzdUsd = response.data.rates[IsoCurrencyPair("NZD", "USD")]
        assertThat(nzdUsd).isNotNull
        assertThat(nzdUsd!!.rate).isGreaterThan(BigDecimal.ZERO)
    }

    @Test
    fun `should throw when no cached rates exist and providers fail`() {
        `when`(fxRateRepository.findByDateRange(mondayDate)).thenReturn(emptyList())
        `when`(fxProviderService.getRates(mondayString, null)).thenReturn(emptyList())
        `when`(fxProviderService.getDefaultProviderId()).thenReturn("FRANKFURTER")
        // No cached rates at all
        `when`(fxRateRepository.findMostRecentBefore(mondayDate)).thenReturn(emptyList())

        val request =
            FxRequest(
                rateDate = mondayString,
                pairs = mutableSetOf(IsoCurrencyPair("NZD", "USD"))
            )

        assertThatThrownBy { fxRateService.getRates(request, "test-token") }
            .isInstanceOf(SystemException::class.java)
            .hasMessageContaining("No rates found")
    }

    @Test
    fun `should not fall back when providers succeed`() {
        val mondayRates =
            listOf(
                FxRate(
                    from = USD,
                    to = NZD,
                    rate = BigDecimal("1.64"),
                    date = mondayDate,
                    provider = "FRANKFURTER"
                )
            )

        // No cached rates for Monday triggers provider fetch
        `when`(fxRateRepository.findByDateRange(mondayDate)).thenReturn(emptyList())
        `when`(fxProviderService.getRates(mondayString, null)).thenReturn(mondayRates)
        `when`(fxProviderService.getDefaultProviderId()).thenReturn("FRANKFURTER")
        // Base rate
        `when`(fxRateRepository.findBaseRate(USD)).thenReturn(
            FxRate(
                from = USD,
                to = USD,
                rate = BigDecimal.ONE,
                date = LocalDate.of(1900, 1, 1)
            )
        )

        val request =
            FxRequest(
                rateDate = mondayString,
                pairs = mutableSetOf(IsoCurrencyPair("NZD", "USD"))
            )

        val response = fxRateService.getRates(request, "test-token")

        assertThat(response.data.rates).isNotEmpty
        val nzdUsd = response.data.rates[IsoCurrencyPair("NZD", "USD")]
        assertThat(nzdUsd).isNotNull
    }
}