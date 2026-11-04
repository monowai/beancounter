package com.beancounter.marketdata.fx

import com.beancounter.common.model.FxRate
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyConfig
import com.beancounter.marketdata.currency.CurrencyService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * A pair's rate history comes from the stored USD->X rates: direct, inverted or crossed.
 */
class FxHistoryServiceTest {
    private val fxRateRepository = mock<FxRateRepository>()
    private val currencyService = mock<CurrencyService>()
    private val fxHistoryService = FxHistoryService(currencyService, fxRateRepository)

    private val friday = LocalDate.of(2024, 1, 19)

    @BeforeEach
    fun setUp() {
        val currencyConfig = CurrencyConfig()
        currencyConfig.baseCurrency = USD
        whenever(currencyService.currencyConfig).thenReturn(currencyConfig)
        whenever(currencyService.getCode("USD")).thenReturn(USD)
        whenever(currencyService.getCode("NZD")).thenReturn(NZD)
        whenever(currencyService.getCode("SGD")).thenReturn(SGD)
    }

    @Test
    fun `should chart USD to a currency over the last months straight from the stored rates`() {
        whenever(fxRateRepository.findHistoricalRatesFromBase(eq(USD), eq(NZD), any(), any())).thenReturn(
            listOf(FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = friday))
        )

        val history = fxHistoryService.getHistoricalRates("USD", "NZD", 3)

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

        val history = fxHistoryService.getHistoricalRates("NZD", "USD", friday)

        assertThat(history.startDate).isEqualTo(friday)
        assertThat(history.data.single().date).isEqualTo(friday)
        assertThat(history.data.single().rate).isEqualByComparingTo("0.625")
    }

    @Test
    fun `should skip a zero stored rate in direct history`() {
        val monday = friday.plusDays(3)
        whenever(fxRateRepository.findHistoricalRatesFromBase(eq(USD), eq(NZD), eq(friday), any())).thenReturn(
            listOf(
                FxRate(from = USD, to = NZD, rate = BigDecimal.ZERO, date = friday),
                FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = monday)
            )
        )

        val history = fxHistoryService.getHistoricalRates("USD", "NZD", friday)

        assertThat(history.data.map { it.date }).containsExactly(monday)
    }

    @Test
    fun `should skip a zero stored rate instead of failing an inverted history`() {
        val monday = friday.plusDays(3)
        whenever(fxRateRepository.findHistoricalRatesFromBase(eq(USD), eq(NZD), eq(friday), any())).thenReturn(
            listOf(
                FxRate(from = USD, to = NZD, rate = BigDecimal.ZERO, date = friday),
                FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = monday)
            )
        )

        val history = fxHistoryService.getHistoricalRates("NZD", "USD", friday)

        assertThat(history.data.map { it.date }).containsExactly(monday)
        assertThat(history.data.single().rate).isEqualByComparingTo("0.625")
    }

    @Test
    fun `should skip a date where either leg of a cross rate is zero`() {
        val monday = friday.plusDays(3)
        val tuesday = friday.plusDays(4)
        whenever(fxRateRepository.findHistoricalRatesFromBase(eq(USD), eq(NZD), eq(friday), any())).thenReturn(
            listOf(
                FxRate(from = USD, to = NZD, rate = BigDecimal.ZERO, date = friday),
                FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = monday),
                FxRate(from = USD, to = NZD, rate = BigDecimal("1.6"), date = tuesday)
            )
        )
        whenever(fxRateRepository.findHistoricalRatesFromBase(eq(USD), eq(SGD), eq(friday), any())).thenReturn(
            listOf(
                FxRate(from = USD, to = SGD, rate = BigDecimal("1.3"), date = friday),
                FxRate(from = USD, to = SGD, rate = BigDecimal.ZERO, date = monday),
                FxRate(from = USD, to = SGD, rate = BigDecimal("1.2"), date = tuesday)
            )
        )

        val history = fxHistoryService.getHistoricalRates("NZD", "SGD", friday)

        assertThat(history.data.map { it.date }).containsExactly(tuesday)
        assertThat(history.data.single().rate).isEqualByComparingTo("0.75")
    }
}