package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.FxService
import com.beancounter.client.services.PriceService
import com.beancounter.client.services.TrnService
import com.beancounter.common.contracts.PerformanceData
import com.beancounter.common.contracts.PerformanceDataPoint
import com.beancounter.common.model.Currency
import com.beancounter.common.model.Portfolio
import com.beancounter.common.model.SystemUser
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.accumulation.Accumulator
import com.beancounter.position.cache.PerformanceCacheService
import com.beancounter.position.irr.TwrCalculator
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
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