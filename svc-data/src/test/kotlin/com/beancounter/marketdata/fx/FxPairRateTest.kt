package com.beancounter.marketdata.fx

import com.beancounter.common.contracts.FxRequest
import com.beancounter.common.exception.SystemException
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyConfig
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.persistence.ConflictTolerantWriter
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A pair rate and its history come from the stored USD->X rates, whichever leg is USD.
 */
class FxPairRateTest {
    private val fxRateRepository = mock<FxRateRepository>()
    private val fxProviderService = mock<FxProviderService>()
    private val currencyService = mock<CurrencyService>()
    private val marketService = mock<MarketService>()
    private lateinit var fxRateService: FxRateService

    private val baseRate = FxRate(from = USD, to = USD, rate = BigDecimal.ONE, date = LocalDate.of(1900, 1, 1))
    private val friday = LocalDate.of(2024, 1, 19)

    @BeforeEach
    fun setUp() {
        val currencyConfig = CurrencyConfig()
        currencyConfig.baseCurrency = USD
        whenever(currencyService.currencyConfig).thenReturn(currencyConfig)
        whenever(currencyService.getCode("USD")).thenReturn(USD)
        whenever(currencyService.getCode("NZD")).thenReturn(NZD)
        whenever(currencyService.getCode("SGD")).thenReturn(SGD)
        whenever(marketService.getMarket("US")).thenReturn(Market("US"))
        whenever(fxProviderService.getDefaultProviderId()).thenReturn("FRANKFURTER")
        whenever(fxRateRepository.findBaseRate(USD)).thenReturn(baseRate)
        fxRateService =
            FxRateService(
                fxProviderService = fxProviderService,
                currencyService = currencyService,
                marketService = marketService,
                marketUtils = PreviousClosePriceDate(DateUtils()),
                fxRateRepository = fxRateRepository,
                conflictTolerantWriter = mock<ConflictTolerantWriter>()
            )
    }

    // Stored USD->NZD rate on whichever date is asked for, without the USD base rate row.
    private fun storeNzdOnRequestedDate() {
        whenever(fxRateRepository.findByDateRange(any(), any())).thenAnswer { call ->
            listOf(FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = call.getArgument(0)))
        }
    }

    @Test
    fun `should price a USD based pair at the stored rate for today`() {
        storeNzdOnRequestedDate()

        val usdNzd = fxRateService.getPairRate("USD", "NZD")

        assertThat(usdNzd.rate).isEqualByComparingTo("1.6")
        assertThat(usdNzd.date).isEqualTo(fxRateService.getDate(DateUtils.TODAY))
        verify(fxProviderService, never()).getRates(any(), anyOrNull())
    }

    @Test
    fun `should price a USD quoted pair at the inverse of the stored rate`() {
        storeNzdOnRequestedDate()

        val nzdUsd = fxRateService.getPairRate("NZD", "USD", friday.toString())

        assertThat(nzdUsd.rate).isEqualByComparingTo("0.625")
        assertThat(nzdUsd.date).isEqualTo(friday)
    }

    @Test
    fun `should fail a pair rate when no rates are stored or fetchable`() {
        whenever(fxRateRepository.findByDateRange(any(), any())).thenReturn(emptyList())
        whenever(fxProviderService.getRates(any(), anyOrNull())).thenReturn(emptyList())
        whenever(fxRateRepository.findMostRecentBefore(any(), any())).thenReturn(emptyList())

        assertThatThrownBy { fxRateService.getPairRate("NZD", "SGD", friday.toString()) }
            .isInstanceOf(SystemException::class.java)
            .hasMessageContaining("No rates found")
    }

    @Test
    fun `should read only the named provider's rates in comparison mode`() {
        whenever(fxRateRepository.findByDateRangeAndProvider(eq(friday), eq("ECB"), any())).thenReturn(
            listOf(baseRate, FxRate(from = USD, to = NZD, rate = BigDecimal("1.7"), date = friday, provider = "ECB"))
        )

        val response =
            fxRateService.getRates(
                FxRequest(
                    rateDate = friday.toString(),
                    pairs = mutableSetOf(IsoCurrencyPair("USD", "NZD")),
                    provider = "ECB"
                ),
                "token"
            )

        assertThat(response.data.rates[IsoCurrencyPair("USD", "NZD")]?.rate).isEqualByComparingTo("1.7")
        verify(fxRateRepository, never()).findByDateRange(any(), any())
    }

    @Test
    fun `should chart USD to a currency over the last months straight from the stored rates`() {
        whenever(fxRateRepository.findHistoricalRatesFromBase(eq(USD), eq(NZD), any(), any())).thenReturn(
            listOf(FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = friday))
        )

        val history = fxRateService.getHistoricalRates("USD", "NZD", 3)

        assertThat(history.startDate).isEqualTo(history.endDate.minusMonths(3))
        assertThat(history.data.map { it.rate })
            .usingElementComparator { a, b -> a.compareTo(b) }
            .containsExactly(BigDecimal("1.6"))
    }

    @Test
    fun `should backfill a currency to USD history as the inverse of the stored rates`() {
        whenever(fxRateRepository.findHistoricalRatesFromBase(eq(USD), eq(NZD), eq(friday), any())).thenReturn(
            listOf(FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = friday))
        )

        val history = fxRateService.getHistoricalRates("NZD", "USD", friday)

        assertThat(history.startDate).isEqualTo(friday)
        assertThat(history.data.single().date).isEqualTo(friday)
        assertThat(history.data.single().rate).isEqualByComparingTo("0.625")
    }
}