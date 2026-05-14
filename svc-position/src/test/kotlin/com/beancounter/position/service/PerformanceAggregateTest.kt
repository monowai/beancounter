package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.BulkFxResponse
import com.beancounter.common.contracts.FxPairResults
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.model.Currency
import com.beancounter.common.model.FxRate
import com.beancounter.common.model.IsoCurrencyPair
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.cache.CachedSnapshot
import com.beancounter.position.cache.PerformanceCacheService
import com.beancounter.position.irr.TwrCalculator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [PerformanceService.composeAggregate] — the pure composition
 * step that combines per-portfolio TWR series into a single display-currency
 * series with chained sub-period AUM-weighted composite TWR.
 *
 * Tests call `composeAggregate` directly with curated `PerformanceData`
 * inputs, bypassing transaction / price / FX clients. The wider `aggregate`
 * method is covered separately at the controller level.
 */
@ExtendWith(MockitoExtension::class)
class PerformanceAggregateTest {
    @Mock
    private lateinit var trnService: TrnService

    @Mock
    private lateinit var accumulator: Accumulator

    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var fxRateService: FxService

    @Mock
    private lateinit var tokenService: TokenService

    @Mock
    private lateinit var cacheService: PerformanceCacheService

    private lateinit var service: PerformanceService

    private val usd = Currency("USD")
    private val nzd = Currency("NZD")
    private val owner =
        SystemUser(
            id = "u",
            email = "u@u",
            true,
            since = DateUtils().getFormattedDate("2020-01-01")
        )

    @BeforeEach
    fun setup() {
        service =
            PerformanceService(
                trnService = trnService,
                accumulator = accumulator,
                priceService = priceService,
                fxRateService = fxRateService,
                twrCalculator = TwrCalculator(),
                dateUtils = DateUtils(),
                tokenService = tokenService,
                cacheService = cacheService
            )
    }

    private fun portfolio(
        code: String,
        ccy: Currency
    ): Portfolio =
        Portfolio(
            id = code,
            code = code,
            name = code,
            currency = ccy,
            base = ccy,
            owner = owner
        )

    private fun point(
        date: String,
        growthOf1000: String,
        marketValue: String,
        netContributions: String = "0",
        cumulativeDividends: String = "0"
    ): PerformanceDataPoint =
        PerformanceDataPoint(
            date = LocalDate.parse(date),
            growthOf1000 = BigDecimal(growthOf1000),
            marketValue = BigDecimal(marketValue),
            netContributions = BigDecimal(netContributions),
            cumulativeReturn =
                BigDecimal(growthOf1000)
                    .divide(BigDecimal("1000"), 6, java.math.RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE),
            cumulativeDividends = BigDecimal(cumulativeDividends)
        )

    @Test
    fun `empty portfolio list returns empty series`() {
        val result =
            service.composeAggregate(
                perPortfolio = emptyList(),
                displayCurrency = usd,
                fxRates = emptyMap()
            )
        assertThat(result.data.series).isEmpty()
        assertThat(result.data.currency).isEqualTo(usd)
    }

    @Test
    fun `single portfolio mirrors composite TWR with period-relative metrics`() {
        val p = portfolio("P1", usd)
        val series =
            listOf(
                point("2025-01-01", "1000", "100000", netContributions = "80000"),
                point("2025-12-01", "1100", "115000", netContributions = "85000")
            )

        val result =
            service.composeAggregate(
                perPortfolio = listOf(p to PerformanceData(usd, series)),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )

        val out = result.data.series
        assertThat(out).hasSize(2)
        assertThat(out[1].growthOf1000).isEqualByComparingTo("1100")
        assertThat(out[1].cumulativeReturn).isEqualByComparingTo("0.1")
        assertThat(out[0].netContributions).isEqualByComparingTo("0")
        assertThat(out[0].investmentGain).isEqualByComparingTo("0")
        assertThat(out[1].netContributions).isEqualByComparingTo("5000")
        assertThat(out[1].investmentGain).isEqualByComparingTo("10000")
        assertThat(out[1].lifetimeContributions).isEqualByComparingTo("85000")
    }

    @Test
    fun `chained composite reweights AUM each sub-period`() {
        val p1 = portfolio("P1", usd)
        val p2 = portfolio("P2", usd)
        val p1Series =
            listOf(
                point("2025-01-01", "1000", "60000", netContributions = "60000"),
                point("2025-06-01", "1100", "66000", netContributions = "60000"),
                point("2025-12-01", "1100", "66000", netContributions = "60000")
            )
        val p2Series =
            listOf(
                point("2025-01-01", "1000", "40000", netContributions = "40000"),
                point("2025-06-01", "1000", "40000", netContributions = "40000"),
                point("2025-12-01", "1200", "48000", netContributions = "40000")
            )

        val result =
            service.composeAggregate(
                perPortfolio =
                    listOf(
                        p1 to PerformanceData(usd, p1Series),
                        p2 to PerformanceData(usd, p2Series)
                    ),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )

        val out = result.data.series
        assertThat(out).hasSize(3)
        // Sub1 (Jan→Jun): w=[0.6,0.4], r=[1.10,1.00] → 1.06
        // Sub2 (Jun→Dec): w=[66/106,40/106], r=[1.00,1.20] → 1.0754716...
        // cum = 1.06 * 1.0754716 ≈ 1.140
        val sub1 = 1.06
        val sub2 = (66.0 / 106.0) + (40.0 / 106.0) * 1.20
        val expected = sub1 * sub2
        assertThat(out[2].growthOf1000.toDouble())
            .isCloseTo(1000.0 * expected, Offset.offset(0.5))
        assertThat(out[2].marketValue).isEqualByComparingTo("114000")
    }

    @Test
    fun `portfolio joining mid-window with zero anchor still contributes once active`() {
        val p1 = portfolio("P1", usd)
        val p2 = portfolio("P2", usd)
        val p1Series =
            listOf(
                point("2025-01-01", "1000", "100000", netContributions = "100000"),
                point("2025-06-01", "1000", "100000", netContributions = "100000"),
                point("2025-12-01", "1000", "100000", netContributions = "100000")
            )
        val p2Series =
            listOf(
                point("2025-01-01", "1000", "0", netContributions = "0"),
                point("2025-06-01", "1000", "20000", netContributions = "20000"),
                point("2025-12-01", "1100", "22000", netContributions = "20000")
            )

        val result =
            service.composeAggregate(
                perPortfolio =
                    listOf(
                        p1 to PerformanceData(usd, p1Series),
                        p2 to PerformanceData(usd, p2Series)
                    ),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )

        val out = result.data.series
        // Sub1: P2 inactive (mv=0) → subReturn = P1.r = 1.0
        // Sub2: w_P1=100/120, w_P2=20/120. r_P1=1.0, r_P2=1.10
        //   subReturn = (100 + 20*1.10) / 120 = 122/120 = 1.01667
        val sub2 = (100.0 + 20.0 * 1.10) / 120.0
        assertThat(out[2].growthOf1000.toDouble())
            .isCloseTo(1000.0 * sub2, Offset.offset(0.5))
        // investmentGain: mv 122k − mv0 100k − periodContrib 20k = 2k (P2's gain)
        assertThat(out[2].investmentGain).isEqualByComparingTo("2000")
        assertThat(out[2].netContributions).isEqualByComparingTo("20000")
        assertThat(out[2].lifetimeContributions).isEqualByComparingTo("120000")
    }

    @Test
    fun `cumulativeDividends accumulates period-relative across snapshots`() {
        // Lifetime dividends from backend at series[0] are excluded from the
        // period total. Subsequent snapshots show only the within-window delta.
        val p = portfolio("P1", usd)
        val series =
            listOf(
                point("2025-01-01", "1000", "100000", netContributions = "100000", cumulativeDividends = "500"),
                point("2025-06-01", "1050", "105000", netContributions = "100000", cumulativeDividends = "750"),
                point("2025-12-01", "1100", "110000", netContributions = "100000", cumulativeDividends = "1200")
            )

        val result =
            service.composeAggregate(
                perPortfolio = listOf(p to PerformanceData(usd, series)),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )

        val out = result.data.series
        assertThat(out[0].cumulativeDividends).isEqualByComparingTo("0")
        // Jun: 750 - baseline 500 = 250
        assertThat(out[1].cumulativeDividends).isEqualByComparingTo("250")
        // Dec: 1200 - baseline 500 = 700
        assertThat(out[2].cumulativeDividends).isEqualByComparingTo("700")
    }

    @Test
    fun `non-positive growthFactor at sub-period boundary is skipped`() {
        // Defensive: a backend bug or extreme drawdown could land growthOf1000
        // at 0. The sub-period weighting must skip that portfolio rather than
        // divide by zero, falling back to the other portfolio's pure return.
        val p1 = portfolio("P1", usd)
        val p2 = portfolio("P2", usd)
        val p1Series =
            listOf(
                point("2025-01-01", "1000", "50000", netContributions = "50000"),
                point("2025-12-01", "1200", "60000", netContributions = "50000")
            )
        val p2Series =
            listOf(
                point("2025-01-01", "0", "50000", netContributions = "50000"), // zero growthFactor
                point("2025-12-01", "0", "50000", netContributions = "50000")
            )

        val result =
            service.composeAggregate(
                perPortfolio =
                    listOf(
                        p1 to PerformanceData(usd, p1Series),
                        p2 to PerformanceData(usd, p2Series)
                    ),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )

        // P2 skipped from weighting (signum check). P1 alone drives composite:
        // r_P1 = 1.2 / 1.0 = 1.20. cum = 1.20. growthOf1000 = 1200.
        assertThat(
            result.data.series
                .last()
                .growthOf1000
                .toDouble()
        ).isCloseTo(1200.0, Offset.offset(0.5))
    }

    @Test
    fun `empty per-portfolio series produces empty result`() {
        val p = portfolio("P1", usd)
        val result =
            service.composeAggregate(
                perPortfolio = listOf(p to PerformanceData(usd, emptyList())),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )
        assertThat(result.data.series).isEmpty()
    }

    @Test
    fun `missing FX rate defaults to 1_0`() {
        // Defensive path: if `fetchDisplayCurrencyRates` failed to find a pair
        // (e.g. FX service returned no rate for an exotic currency), the map
        // lookup is null and we apply 1.0 rather than blow up the whole
        // aggregate. Worth pinning so this fallback isn't quietly removed.
        val p = portfolio("P1", nzd)
        val series =
            listOf(
                point("2025-01-01", "1000", "100", netContributions = "100"),
                point("2025-12-01", "1100", "110", netContributions = "100")
            )

        val result =
            service.composeAggregate(
                perPortfolio = listOf(p to PerformanceData(nzd, series)),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE) // NZD missing
            )

        // No FX applied → MV passes through unchanged in nominal terms.
        assertThat(result.data.series).hasSize(2)
        assertThat(result.data.series[0].marketValue).isEqualByComparingTo("100")
        assertThat(result.data.series[1].marketValue).isEqualByComparingTo("110")
    }

    @Test
    fun `single-point series yields one row with zero period metrics`() {
        // Boundary: cache may have only the synthesized anchor (no later data).
        val p = portfolio("P1", usd)
        val result =
            service.composeAggregate(
                perPortfolio =
                    listOf(
                        p to
                            PerformanceData(
                                usd,
                                listOf(point("2025-01-01", "1000", "5000", netContributions = "5000"))
                            )
                    ),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )

        assertThat(result.data.series).hasSize(1)
        val only = result.data.series.first()
        assertThat(only.growthOf1000).isEqualByComparingTo("1000")
        assertThat(only.cumulativeReturn).isEqualByComparingTo("0")
        assertThat(only.netContributions).isEqualByComparingTo("0")
        assertThat(only.investmentGain).isEqualByComparingTo("0")
        assertThat(only.lifetimeContributions).isEqualByComparingTo("5000")
    }

    @Test
    fun `all portfolios inactive at t0 yields composite return 1_0 until first activity`() {
        // Two portfolios, both anchored at startDate with mv=0. P1 activates at
        // Jun, P2 at Dec. Sub-period Jan→Jun has no active AUM (both mv=0),
        // so subReturn falls back to 1.0 — composite cum factor stays at 1.0.
        val p1 = portfolio("P1", usd)
        val p2 = portfolio("P2", usd)
        val p1Series =
            listOf(
                point("2025-01-01", "1000", "0", netContributions = "0"),
                point("2025-06-01", "1000", "10000", netContributions = "10000"),
                point("2025-12-01", "1100", "11000", netContributions = "10000")
            )
        val p2Series =
            listOf(
                point("2025-01-01", "1000", "0", netContributions = "0"),
                point("2025-06-01", "1000", "0", netContributions = "0"),
                point("2025-12-01", "1000", "5000", netContributions = "5000")
            )

        val result =
            service.composeAggregate(
                perPortfolio =
                    listOf(
                        p1 to PerformanceData(usd, p1Series),
                        p2 to PerformanceData(usd, p2Series)
                    ),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE)
            )

        val out = result.data.series
        // i=0 (Jan): baseline.
        assertThat(out[0].growthOf1000).isEqualByComparingTo("1000")
        // i=1 (Jun): only P1 active by then. Both prev (i=0) mv were 0, so
        // sub-period Jan→Jun = no weighted portfolios → subReturn=1.0.
        assertThat(out[1].growthOf1000).isEqualByComparingTo("1000")
        // i=2 (Dec): Jun→Dec, P1 prev mv=10k weighted. P2 prev mv=0 excluded.
        // r_P1 = 1100/1000 = 1.10. subReturn = 1.10. cum = 1.10.
        assertThat(out[2].growthOf1000.toDouble())
            .isCloseTo(1100.0, Offset.offset(0.5))
    }

    @Test
    fun `aggregate with empty portfolio list short-circuits before any IO`() {
        val result = service.aggregate(emptyList(), 12, usd)

        assertThat(result.data.series).isEmpty()
        assertThat(result.data.currency).isEqualTo(usd)
        verify(fxRateService, never()).getBulkRates(any(), any())
        verify(tokenService, never()).bearerToken
    }

    @Test
    fun `aggregate with single same-currency portfolio skips FX service`() {
        // Cache HIT keeps trnService/priceService out of the picture. Display
        // ccy == portfolio ccy means no IsoCurrencyPair to fetch, so the FX
        // service should never be hit either.
        whenever(cacheService.isAvailable()).thenReturn(true)
        val today = LocalDate.now()
        val snapshots =
            listOf(
                CachedSnapshot(
                    valuationDate = today.minusMonths(12),
                    marketValue = BigDecimal("10000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("10000"),
                    cumulativeDividends = BigDecimal.ZERO
                ),
                CachedSnapshot(
                    valuationDate = today,
                    marketValue = BigDecimal("11000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("10000"),
                    cumulativeDividends = BigDecimal.ZERO
                )
            )
        whenever(cacheService.findAllSnapshots(eq("P1"))).thenReturn(snapshots)

        val result = service.aggregate(listOf(portfolio("P1", usd)), 12, usd)

        assertThat(result.data.series).isNotEmpty()
        assertThat(result.data.currency).isEqualTo(usd)
        verify(fxRateService, never()).getBulkRates(any(), any())
    }

    @Test
    fun `aggregate fetches FX bulk rates for foreign-currency portfolios`() {
        whenever(cacheService.isAvailable()).thenReturn(true)
        val today = LocalDate.now()
        val nzdSnapshots =
            listOf(
                CachedSnapshot(
                    valuationDate = today.minusMonths(12),
                    marketValue = BigDecimal("100000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("100000"),
                    cumulativeDividends = BigDecimal.ZERO
                ),
                CachedSnapshot(
                    valuationDate = today,
                    marketValue = BigDecimal("110000"),
                    externalCashFlow = BigDecimal.ZERO,
                    netContributions = BigDecimal("100000"),
                    cumulativeDividends = BigDecimal.ZERO
                )
            )
        whenever(cacheService.findAllSnapshots(eq("NZPF"))).thenReturn(nzdSnapshots)

        val pair = IsoCurrencyPair("NZD", "USD")
        val rate = FxRate(from = nzd, to = usd, rate = BigDecimal("0.6"), date = today)
        val fxResponse =
            BulkFxResponse(
                data = mapOf(today.toString() to FxPairResults(rates = mapOf(pair to rate)))
            )
        lenient().`when`(tokenService.bearerToken).thenReturn("test-token")
        whenever(fxRateService.getBulkRates(any(), any())).thenReturn(fxResponse)

        val result = service.aggregate(listOf(portfolio("NZPF", nzd)), 12, usd)

        assertThat(result.data.currency).isEqualTo(usd)
        // 100k NZD at 0.6 → 60k USD initial; 110k NZD at 0.6 → 66k USD end.
        assertThat(
            result.data.series
                .first()
                .marketValue
        ).isEqualByComparingTo("60000")
        assertThat(
            result.data.series
                .last()
                .marketValue
        ).isEqualByComparingTo("66000")
        verify(fxRateService).getBulkRates(any(), any())
    }

    @Test
    fun `FX converts portfolio MV and contributions to display currency`() {
        val pUsd = portfolio("USPF", usd)
        val pNzd = portfolio("NZPF", nzd)
        val usdSeries =
            listOf(
                point("2025-01-01", "1000", "50000", netContributions = "50000"),
                point("2025-12-01", "1100", "55000", netContributions = "50000")
            )
        val nzdSeries =
            listOf(
                point("2025-01-01", "1000", "100000", netContributions = "100000"),
                point("2025-12-01", "1200", "120000", netContributions = "100000")
            )

        val result =
            service.composeAggregate(
                perPortfolio =
                    listOf(
                        pUsd to PerformanceData(usd, usdSeries),
                        pNzd to PerformanceData(nzd, nzdSeries)
                    ),
                displayCurrency = usd,
                fxRates = mapOf("USD" to BigDecimal.ONE, "NZD" to BigDecimal("0.6"))
            )

        val out = result.data.series
        assertThat(out[0].marketValue).isEqualByComparingTo("110000")
        assertThat(out[1].marketValue).isEqualByComparingTo("127000")
        val expected = (50.0 / 110.0) * 1.10 + (60.0 / 110.0) * 1.20
        assertThat(out[1].growthOf1000.toDouble())
            .isCloseTo(1000.0 * expected, Offset.offset(0.5))
    }
}