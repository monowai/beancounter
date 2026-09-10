package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [TreasuryYieldService] with [TreasuryYieldFetcher] mocked. Pins the sparse
 * point-selection and changeBps arithmetic, and the "omit rather than fail" contract for a
 * maturity with no upstream data.
 */
class TreasuryYieldServiceTest {
    private val fetcher = mock<TreasuryYieldFetcher>()
    private val service = TreasuryYieldService(fetcher)

    private val today = LocalDate.of(2026, 9, 9)

    private fun point(
        daysAgo: Long,
        value: String
    ): YieldPoint = YieldPoint(today.minusDays(daysAgo), BigDecimal(value))

    @Test
    fun `projects latest, lookback and changeBps for each maturity`() {
        whenever(fetcher.fetch("10year")).thenReturn(
            listOf(
                point(0, "4.83"),
                point(14, "4.58"),
                point(7, "4.70"),
                point(30, "4.40")
            )
        )
        whenever(fetcher.fetch("2year")).thenReturn(emptyList())

        val result = service.getYields(lookbackDays = 14)

        assertThat(result).hasSize(1)
        val us10y = result.first()
        assertThat(us10y.series).isEqualTo("US10Y")
        assertThat(us10y.latest).isEqualByComparingTo(BigDecimal("4.83"))
        assertThat(us10y.latestDate).isEqualTo(today)
        assertThat(us10y.lookback).isEqualByComparingTo(BigDecimal("4.58"))
        assertThat(us10y.lookbackDate).isEqualTo(today.minusDays(14))
        // (4.83 - 4.58) * 100 = 25.00 bps
        assertThat(us10y.changeBps).isEqualByComparingTo(BigDecimal("25.00"))
    }

    @Test
    fun `sparse points cover latest, lookback, 7d and 30d and are sorted ascending by date`() {
        whenever(fetcher.fetch("10year")).thenReturn(
            listOf(
                point(0, "4.83"),
                point(7, "4.70"),
                point(14, "4.58"),
                point(30, "4.40")
            )
        )
        whenever(fetcher.fetch("2year")).thenReturn(emptyList())

        val result = service.getYields(lookbackDays = 14)

        val points = result.first().points
        assertThat(points.map { it.date }).isSorted()
        assertThat(points.map { it.date }).containsExactly(
            today.minusDays(30),
            today.minusDays(14),
            today.minusDays(7),
            today
        )
    }

    @Test
    fun `nearest point is picked when the exact lookback day is missing`() {
        // No point exactly 14 days back — nearest available (13 days back) is used instead.
        whenever(fetcher.fetch("10year")).thenReturn(
            listOf(
                point(0, "4.83"),
                point(13, "4.60")
            )
        )
        whenever(fetcher.fetch("2year")).thenReturn(emptyList())

        val result = service.getYields(lookbackDays = 14)

        assertThat(result.first().lookback).isEqualByComparingTo(BigDecimal("4.60"))
        assertThat(result.first().lookbackDate).isEqualTo(today.minusDays(13))
    }

    @Test
    fun `a maturity with no upstream data is omitted, not null or thrown`() {
        whenever(fetcher.fetch("10year")).thenReturn(emptyList())
        whenever(fetcher.fetch("2year")).thenReturn(emptyList())

        val result = service.getYields(lookbackDays = 14)

        assertThat(result).isEmpty()
    }

    @Test
    fun `both maturities present are each projected independently`() {
        whenever(fetcher.fetch("10year")).thenReturn(listOf(point(0, "4.83"), point(14, "4.58")))
        whenever(fetcher.fetch("2year")).thenReturn(listOf(point(0, "3.60"), point(14, "3.90")))

        val result = service.getYields(lookbackDays = 14)

        assertThat(result.map { it.series }).containsExactlyInAnyOrder("US10Y", "US2Y")
        val us2y = result.first { it.series == "US2Y" }
        // (3.60 - 3.90) * 100 = -30.00 bps
        assertThat(us2y.changeBps).isEqualByComparingTo(BigDecimal("-30.00"))
    }

    @Test
    fun `default lookback is applied when the caller omits it`() {
        whenever(fetcher.fetch(any())).thenReturn(listOf(point(0, "4.83"), point(14, "4.58")))

        val result = service.getYields()

        assertThat(result).isNotEmpty()
    }
}