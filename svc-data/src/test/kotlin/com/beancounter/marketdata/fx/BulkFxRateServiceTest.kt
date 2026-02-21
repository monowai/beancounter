package com.beancounter.marketdata.fx

import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyConfig
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.markets.MarketService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.math.BigDecimal
import java.time.LocalDate

class BulkFxRateServiceTest {
    private lateinit var fxRateService: FxRateService
    private lateinit var fxRateRepository: FxRateRepository
    private val fxProviderService = mock(FxProviderService::class.java)
    private val currencyService = mock(CurrencyService::class.java)
    private val marketService = mock(MarketService::class.java)

    @BeforeEach
    fun setUp() {
        fxRateRepository = mock(FxRateRepository::class.java)
        val currencyConfig = CurrencyConfig()
        currencyConfig.baseCurrency = USD
        `when`(currencyService.currencyConfig).thenReturn(currencyConfig)
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
    fun `getBulkRates returns empty for no pairs`() {
        val request = BulkFxRequest(startDate = "2024-01-01", endDate = "2024-01-31")
        val result = fxRateService.getBulkRates(request)
        assertThat(result.data).isEmpty()
    }

    @Test
    fun `getBulkRates returns rates grouped by date`() {
        val date1 = LocalDate.of(2024, 1, 15)
        val date2 = LocalDate.of(2024, 1, 16)
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 1, 31)

        val nzdRate1 =
            FxRate(
                from = USD,
                to = NZD,
                rate = BigDecimal("1.55"),
                date = date1
            )
        val nzdRate2 =
            FxRate(
                from = USD,
                to = NZD,
                rate = BigDecimal("1.56"),
                date = date2
            )
        val usdRate1 =
            FxRate(
                from = USD,
                to = USD,
                rate = BigDecimal.ONE,
                date = date1
            )
        val usdRate2 =
            FxRate(
                from = USD,
                to = USD,
                rate = BigDecimal.ONE,
                date = date2
            )

        `when`(fxRateRepository.findByDateBetween(startDate, endDate))
            .thenReturn(listOf(nzdRate1, nzdRate2, usdRate1, usdRate2))
        `when`(fxRateRepository.findBaseRate(USD))
            .thenReturn(
                FxRate(
                    from = USD,
                    to = USD,
                    rate = BigDecimal.ONE,
                    date = LocalDate.of(1900, 1, 1)
                )
            )

        val request =
            BulkFxRequest(
                startDate = "2024-01-01",
                endDate = "2024-01-31",
                pairs = setOf(IsoCurrencyPair("NZD", "USD"))
            )

        val result = fxRateService.getBulkRates(request)

        assertThat(result.data).hasSize(2)
        assertThat(result.data).containsKey("2024-01-15")
        assertThat(result.data).containsKey("2024-01-16")

        // NZD->USD should be 1/1.55 = ~0.64516129
        val nzdUsdRate = result.data["2024-01-15"]!!.rates[IsoCurrencyPair("NZD", "USD")]
        assertThat(nzdUsdRate).isNotNull
        assertThat(nzdUsdRate!!.rate).isGreaterThan(BigDecimal.ZERO)
    }

    @Test
    fun `getBulkRates handles dates with no cached rates gracefully`() {
        val startDate = LocalDate.of(2024, 1, 1)
        val endDate = LocalDate.of(2024, 1, 31)

        // No rates in the DB at all
        `when`(fxRateRepository.findByDateBetween(startDate, endDate))
            .thenReturn(emptyList())
        `when`(fxRateRepository.findBaseRate(USD)).thenReturn(null)

        val request =
            BulkFxRequest(
                startDate = "2024-01-01",
                endDate = "2024-01-31",
                pairs = setOf(IsoCurrencyPair("NZD", "USD"))
            )

        val result = fxRateService.getBulkRates(request)

        // No cached dates means no results
        assertThat(result.data).isEmpty()
    }
}