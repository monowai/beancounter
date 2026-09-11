package com.beancounter.marketdata.macro

import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.eodhd.EodhdConfig
import com.beancounter.marketdata.providers.eodhd.EodhdProxy
import com.beancounter.marketdata.providers.eodhd.model.EodhdPrice
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Unit tests for [MacroIndicatorsService] with [EodhdProxy] and [TreasuryYieldService] mocked.
 * Pins: oil changePercent arithmetic, the "omit rather than fail" contract for both yields and
 * oil legs, and that no provider name ever leaks into the response (only the WTI_PROXY /
 * BRENT_PROXY labels and USO / BNO symbols, which are intentionally public).
 */
class MacroIndicatorsServiceTest {
    private val eodhdProxy = mock<EodhdProxy>()
    private val marketService = mock<MarketService>()
    private val eodhdConfig =
        EodhdConfig(marketService).also {
            ReflectionTestUtils.setField(it, "apiKey", "demo")
            ReflectionTestUtils.setField(it, "markets", "")
        }
    private val treasuryYieldService = mock<TreasuryYieldService>()
    private val service = MacroIndicatorsService(eodhdProxy, eodhdConfig, treasuryYieldService)

    private fun price(
        daysAgo: Long,
        close: String,
        today: LocalDate = LocalDate.of(2026, 9, 9)
    ): EodhdPrice = EodhdPrice(date = today.minusDays(daysAgo), close = BigDecimal(close))

    @Test
    fun `returns projected yields and oil series for the given lookback`() {
        whenever(treasuryYieldService.getYields(14)).thenReturn(
            listOf(
                YieldSeries(
                    "US10Y",
                    BigDecimal("4.83"),
                    LocalDate.now(),
                    BigDecimal("4.58"),
                    LocalDate.now(),
                    BigDecimal("25.00"),
                    emptyList()
                )
            )
        )
        whenever(eodhdProxy.getHistory(eq("USO.US"), any(), any(), eq("demo")))
            .thenReturn(listOf(price(0, "158.38"), price(14, "149.10")))
        whenever(eodhdProxy.getHistory(eq("BNO.US"), any(), any(), eq("demo")))
            .thenReturn(listOf(price(0, "80.00"), price(14, "76.00")))

        val result = service.getIndicators(lookbackDays = 14)

        assertThat(result.lookbackDays).isEqualTo(14)
        assertThat(result.yields).hasSize(1)
        assertThat(result.oil.map { it.series }).containsExactlyInAnyOrder("WTI_PROXY", "BRENT_PROXY")
    }

    @Test
    fun `oil changePercent is computed from latest vs nearest-lookback close`() {
        whenever(treasuryYieldService.getYields(any())).thenReturn(emptyList())
        whenever(eodhdProxy.getHistory(eq("USO.US"), any(), any(), eq("demo")))
            .thenReturn(listOf(price(0, "158.38"), price(14, "149.10")))
        whenever(eodhdProxy.getHistory(eq("BNO.US"), any(), any(), eq("demo"))).thenReturn(emptyList())

        val result = service.getIndicators(lookbackDays = 14)

        val wti = result.oil.first { it.series == "WTI_PROXY" }
        assertThat(wti.symbol).isEqualTo("USO")
        assertThat(wti.latest).isEqualByComparingTo(BigDecimal("158.38"))
        assertThat(wti.lookback).isEqualByComparingTo(BigDecimal("149.10"))
        // (158.38 - 149.10) / 149.10 * 100 = 6.22401... -> rounds to 6.2240 at 4dp
        assertThat(wti.changePercent).isEqualByComparingTo(BigDecimal("6.2240"))
    }

    @Test
    fun `oil lookback picks the price at-or-before target even when a later price is closer`() {
        // target = latest(day0) - 3 days = day-3. A price 6 days ago precedes the target; a price
        // 1 day ago comes AFTER it but is closer by absolute distance — the old closest-by-distance
        // selection picked the 1-day-ago price and got the sign of changePercent wrong.
        whenever(treasuryYieldService.getYields(any())).thenReturn(emptyList())
        whenever(eodhdProxy.getHistory(eq("USO.US"), any(), any(), eq("demo"))).thenReturn(
            listOf(price(0, "100.00"), price(1, "90.00"), price(6, "120.00"))
        )
        whenever(eodhdProxy.getHistory(eq("BNO.US"), any(), any(), eq("demo"))).thenReturn(emptyList())

        val result = service.getIndicators(lookbackDays = 3)

        val wti = result.oil.first { it.series == "WTI_PROXY" }
        assertThat(wti.lookback).isEqualByComparingTo(BigDecimal("120.00"))
        // (100 - 120) / 120 * 100 = -16.6667: negative, as it should be — the old strategy would
        // have picked the 90.00 price and produced a spuriously positive +11.11% instead.
        assertThat(wti.changePercent).isEqualByComparingTo(BigDecimal("-16.6667"))
    }

    @Test
    fun `an oil leg with no upstream history is omitted, not null`() {
        whenever(treasuryYieldService.getYields(any())).thenReturn(emptyList())
        whenever(eodhdProxy.getHistory(eq("USO.US"), any(), any(), eq("demo"))).thenReturn(emptyList())
        whenever(eodhdProxy.getHistory(eq("BNO.US"), any(), any(), eq("demo")))
            .thenReturn(listOf(price(0, "80.00"), price(14, "76.00")))

        val result = service.getIndicators(lookbackDays = 14)

        assertThat(result.oil).hasSize(1)
        assertThat(result.oil.first().series).isEqualTo("BRENT_PROXY")
    }

    @Test
    fun `an oil leg whose upstream call throws is omitted rather than failing the whole request`() {
        whenever(treasuryYieldService.getYields(any())).thenReturn(emptyList())
        whenever(eodhdProxy.getHistory(eq("USO.US"), any(), any(), eq("demo")))
            .thenThrow(RuntimeException("EODHD 429"))
        whenever(eodhdProxy.getHistory(eq("BNO.US"), any(), any(), eq("demo")))
            .thenReturn(listOf(price(0, "80.00"), price(14, "76.00")))

        val result = service.getIndicators(lookbackDays = 14)

        assertThat(result.oil).hasSize(1)
        assertThat(result.oil.first().series).isEqualTo("BRENT_PROXY")
    }

    @Test
    fun `yields are omitted entirely rather than failing the whole request when the yield service throws`() {
        whenever(treasuryYieldService.getYields(any())).thenThrow(RuntimeException("AlphaVantage down"))
        whenever(eodhdProxy.getHistory(any(), any(), any(), any())).thenReturn(emptyList())

        val result = service.getIndicators(lookbackDays = 14)

        assertThat(result.yields).isEmpty()
        assertThat(result.oil).isEmpty()
    }

    @Test
    fun `lookbackDays is passed through to the treasury yield service`() {
        whenever(treasuryYieldService.getYields(any())).thenReturn(emptyList())
        whenever(eodhdProxy.getHistory(any(), any(), any(), any())).thenReturn(emptyList())

        service.getIndicators(lookbackDays = 30)

        verify(treasuryYieldService).getYields(30)
    }

    @Test
    fun `asOf is set to today`() {
        whenever(treasuryYieldService.getYields(any())).thenReturn(emptyList())
        whenever(eodhdProxy.getHistory(any(), any(), any(), any())).thenReturn(emptyList())

        val result = service.getIndicators(lookbackDays = 14)

        assertThat(result.asOf).isEqualTo(LocalDate.now())
    }
}