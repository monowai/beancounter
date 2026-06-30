package com.beancounter.position.service

import com.beancounter.auth.TokenService
import com.beancounter.client.services.PriceService
import com.beancounter.common.contracts.BulkPriceRequest
import com.beancounter.common.contracts.BulkPriceResponse
import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.model.MarketData
import com.beancounter.common.model.Portfolio
import com.beancounter.common.utils.DateUtils
import com.beancounter.position.Constants.Companion.USD
import com.beancounter.position.Constants.Companion.owner
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.lenient
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

@ExtendWith(MockitoExtension::class)
class BenchmarkServiceTest {
    @Mock
    private lateinit var priceService: PriceService

    @Mock
    private lateinit var tokenService: TokenService

    private val dateUtils = DateUtils()
    private lateinit var benchmarkService: BenchmarkService

    private val indexMarket = Market("INDEX")
    private val sp500 =
        Asset(
            code = "^GSPC",
            id = "^GSPC",
            name = "S&P 500",
            market = indexMarket
        )
    private val portfolio =
        Portfolio(
            id = "test-pf",
            code = "TEST",
            name = "Test Portfolio",
            currency = USD,
            base = USD,
            owner = owner
        )

    @BeforeEach
    fun setup() {
        lenient().`when`(tokenService.bearerToken).thenReturn("test-token")
        benchmarkService = BenchmarkService(priceService, tokenService, dateUtils)
    }

    @Test
    fun `monthlyDates spans start and end with 1-month cadence`() {
        val start = LocalDate.of(2024, 1, 15)
        val end = LocalDate.of(2024, 4, 15)
        val dates = benchmarkService.monthlyDates(start, end)
        assertThat(dates)
            .containsExactly(
                LocalDate.of(2024, 1, 15),
                LocalDate.of(2024, 2, 15),
                LocalDate.of(2024, 3, 15),
                LocalDate.of(2024, 4, 15)
            )
    }

    @Test
    fun `monthlyDates appends end date when last cadence step misses it`() {
        val start = LocalDate.of(2024, 1, 1)
        val end = LocalDate.of(2024, 2, 20)
        val dates = benchmarkService.monthlyDates(start, end)
        assertThat(dates).contains(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 2, 1), end)
        assertThat(dates.last()).isEqualTo(end)
    }

    /**
     * Month-end anchor: cursor.plusMonths(1) drifts forward by a day after
     * the first short month (e.g. 2026-03-31 → 2026-04-30 → 2026-05-30).
     * Snap each cursor to last-day-of-month so the grid stays on month-ends
     * and doesn't emit a spurious extra point alongside `endDate`.
     */
    @Test
    fun `monthlyDates snaps to month-end when anchor is the last day of its month`() {
        val start = LocalDate.of(2026, 3, 31)
        val end = LocalDate.of(2026, 5, 31)
        val dates = benchmarkService.monthlyDates(start, end)
        assertThat(dates)
            .containsExactly(
                LocalDate.of(2026, 3, 31),
                LocalDate.of(2026, 4, 30),
                LocalDate.of(2026, 5, 31)
            )
    }

    /**
     * Endpoint coincides with a month-end cadence point: it should appear
     * exactly once (no duplicate trailing append).
     */
    @Test
    fun `monthlyDates does not duplicate endDate when it lands on a cadence point`() {
        val start = LocalDate.of(2024, 1, 31)
        val end = LocalDate.of(2024, 3, 31)
        val dates = benchmarkService.monthlyDates(start, end)
        assertThat(dates)
            .containsExactly(
                LocalDate.of(2024, 1, 31),
                LocalDate.of(2024, 2, 29), // leap year
                LocalDate.of(2024, 3, 31)
            )
    }

    @Test
    fun `series rebases first close to 1000 and scales subsequent closes`() {
        val today = dateUtils.date
        val firstDate = today.minusMonths(2)
        val midDate = today.minusMonths(1)
        val bulk =
            BulkPriceResponse(
                mapOf(
                    firstDate.toString() to listOf(priceFor(sp500, firstDate, BigDecimal("4000.00"))),
                    midDate.toString() to listOf(priceFor(sp500, midDate, BigDecimal("4400.00"))),
                    today.toString() to listOf(priceFor(sp500, today, BigDecimal("5000.00")))
                )
            )
        whenever(priceService.getBulkPrices(any<BulkPriceRequest>(), any())).thenReturn(bulk)

        val response = benchmarkService.benchmark(portfolio, sp500.id, months = 2)

        assertThat(response.data.series).hasSize(3)
        assertThat(
            response.data.series
                .first()
                .growthOf1000
        ).isCloseTo(BigDecimal("1000.00"), Offset.offset(BigDecimal("0.01")))
        assertThat(response.data.series[1].growthOf1000)
            .isCloseTo(BigDecimal("1100.00"), Offset.offset(BigDecimal("0.01")))
        assertThat(
            response.data.series
                .last()
                .growthOf1000
        ).isCloseTo(BigDecimal("1250.00"), Offset.offset(BigDecimal("0.01")))
    }

    @Test
    fun `missing exact-date close falls back to nearest prior close`() {
        val today = dateUtils.date
        // Take the slots from the same grid the service builds. monthlyDates snaps
        // to month-ends, so deriving midDate as today.minusMonths(1) drifts off the
        // grid whenever today is a month-end before a longer month (a date-bomb).
        val grid = benchmarkService.monthlyDates(today.minusMonths(2), today)
        val firstDate = grid.first()
        val midDate = grid[1]
        val priorDate = firstDate.minusDays(3)
        val bulk =
            BulkPriceResponse(
                mapOf(
                    // Provide a close BEFORE the firstDate slot and a later close on midDate
                    priorDate.toString() to listOf(priceFor(sp500, priorDate, BigDecimal("4000.00"))),
                    midDate.toString() to listOf(priceFor(sp500, midDate, BigDecimal("4200.00")))
                )
            )
        whenever(priceService.getBulkPrices(any<BulkPriceRequest>(), any())).thenReturn(bulk)

        val response = benchmarkService.benchmark(portfolio, sp500.id, months = 2)

        assertThat(response.data.series).isNotEmpty
        // First slot uses priorDate close (4000) → growthOf1000 = 1000
        assertThat(
            response.data.series
                .first()
                .growthOf1000
        ).isCloseTo(BigDecimal("1000.00"), Offset.offset(BigDecimal("0.01")))
        // Mid slot has its own close (4200) → growthOf1000 = 1050
        val midPoint = response.data.series.first { it.date == midDate }
        assertThat(midPoint.growthOf1000)
            .isCloseTo(BigDecimal("1050.00"), Offset.offset(BigDecimal("0.01")))
    }

    @Test
    fun `empty price response returns empty series with portfolio currency`() {
        whenever(priceService.getBulkPrices(any<BulkPriceRequest>(), any()))
            .thenReturn(BulkPriceResponse(emptyMap()))

        val response = benchmarkService.benchmark(portfolio, sp500.id, months = 6)

        assertThat(response.data.series).isEmpty()
        assertThat(response.data.currency.code).isEqualTo("USD")
    }

    private fun priceFor(
        asset: Asset,
        date: LocalDate,
        close: BigDecimal
    ): MarketData =
        MarketData(
            asset = asset,
            priceDate = date,
            close = close
        )
}