package com.beancounter.marketdata.fx

import com.beancounter.common.contracts.BulkFxRequest
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.GBP
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.SGD
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyConfig
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.fx.fxrates.FxProviderService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.persistence.ConflictTolerantWriter
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
                fxRateRepository = fxRateRepository,
                conflictTolerantWriter = mock(ConflictTolerantWriter::class.java)
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

    private fun usdRate(
        to: Currency,
        rate: String,
        date: LocalDate
    ) = FxRate(from = USD, to = to, rate = BigDecimal(rate), date = date)

    private fun stubBaseRate() {
        `when`(fxRateRepository.findBaseRate(USD))
            .thenReturn(usdRate(USD, "1", LocalDate.of(1900, 1, 1)))
    }

    @Test
    fun `should return supported pairs when one pair has no rate on the date`() {
        val early = LocalDate.of(2026, 1, 6)
        val late = LocalDate.of(2026, 5, 1)
        val thb = Currency("THB")
        `when`(fxRateRepository.findByDateBetween(early.minusDays(10), late))
            .thenReturn(
                listOf(
                    usdRate(GBP, "0.5", early),
                    usdRate(SGD, "1.3", early),
                    usdRate(GBP, "0.5", late),
                    usdRate(SGD, "1.3", late),
                    usdRate(thb, "32", late)
                )
            )
        stubBaseRate()
        val gbpSgd = IsoCurrencyPair("GBP", "SGD")
        val thbSgd = IsoCurrencyPair("THB", "SGD")

        val result =
            fxRateService.getBulkRates(
                BulkFxRequest(
                    startDate = "2026-01-06",
                    endDate = "2026-05-01",
                    dates = listOf("2026-01-06", "2026-05-01"),
                    pairs = setOf(gbpSgd, thbSgd)
                )
            )

        assertThat(result.data).containsOnlyKeys("2026-01-06", "2026-05-01")
        assertThat(result.data.getValue("2026-01-06").rates)
            .containsOnlyKeys(gbpSgd)
        assertThat(
            result.data
                .getValue("2026-01-06")
                .rates
                .getValue(gbpSgd)
                .rate
        ).isEqualByComparingTo("2.6")
        assertThat(result.data.getValue("2026-05-01").rates).containsOnlyKeys(gbpSgd, thbSgd)
    }

    @Test
    fun `should use nearest prior stored date when requested date has no rows`() {
        val monday = LocalDate.of(2026, 2, 23)
        val friday = LocalDate.of(2026, 2, 27)
        val sunday = LocalDate.of(2026, 3, 1)
        `when`(fxRateRepository.findByDateBetween(monday.minusDays(10), sunday))
            .thenReturn(
                listOf(
                    usdRate(GBP, "0.5", monday),
                    usdRate(SGD, "1.3", monday),
                    usdRate(GBP, "0.4", friday),
                    usdRate(SGD, "1.2", friday)
                )
            )
        stubBaseRate()
        val gbpSgd = IsoCurrencyPair("GBP", "SGD")

        val result =
            fxRateService.getBulkRates(
                BulkFxRequest(
                    startDate = "2026-02-23",
                    endDate = "2026-03-01",
                    dates = listOf("2026-02-23", "2026-03-01"),
                    pairs = setOf(gbpSgd)
                )
            )

        assertThat(
            result.data
                .getValue("2026-02-23")
                .rates
                .getValue(gbpSgd)
                .rate
        ).isEqualByComparingTo("2.6")
        // Friday: 1.2 / 0.4 = 3.0 (Monday would give 2.6)
        assertThat(
            result.data
                .getValue("2026-03-01")
                .rates
                .getValue(gbpSgd)
                .rate
        ).isEqualByComparingTo("3.0")
    }
}