package com.beancounter.marketdata.macro

import com.beancounter.common.utils.DateUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Unit tests for [EconomicCalendarService] with [FredSeriesFetcher] and [KalshiGateway] mocked.
 * Pins: how each recent print (and its prior) is derived from the raw monthly series, the implied
 * expectation read off an "Above X" probability ladder, and that one failing leg never removes the
 * others from the response.
 */
class EconomicCalendarServiceTest {
    private val fetcher = mock<FredSeriesFetcher>()
    private val gateway = mock<KalshiGateway>()
    private val dateUtils =
        mock<DateUtils>().also {
            whenever(it.date).thenReturn(LocalDate.of(2026, 11, 10))
            whenever(it.zoneId).thenReturn(ZoneId.of("UTC"))
        }
    private val service = EconomicCalendarService(fetcher, gateway, dateUtils)

    /** Newest-first monthly points, matching what [FredSeriesFetcher.fetch] returns. */
    private fun monthly(vararg values: Pair<String, String>): List<SeriesPoint> =
        values
            .map { (month, value) -> SeriesPoint(YearMonth.parse(month).atDay(1), BigDecimal(value)) }
            .sortedByDescending { it.date }

    private fun stubFred(
        payems: List<SeriesPoint> = emptyList(),
        unrate: List<SeriesPoint> = emptyList(),
        cpiSa: List<SeriesPoint> = emptyList(),
        cpiNsa: List<SeriesPoint> = emptyList(),
        coreNsa: List<SeriesPoint> = emptyList()
    ) {
        whenever(fetcher.fetch("PAYEMS")).thenReturn(payems)
        whenever(fetcher.fetch("UNRATE")).thenReturn(unrate)
        whenever(fetcher.fetch("CPIAUCSL")).thenReturn(cpiSa)
        whenever(fetcher.fetch("CPIAUCNS")).thenReturn(cpiNsa)
        whenever(fetcher.fetch("CPILFENS")).thenReturn(coreNsa)
    }

    private fun noKalshiEvents() {
        whenever(gateway.getEvents(any(), any())).thenReturn(KalshiEventsResponse())
    }

    private fun above(
        strike: String,
        bid: String,
        ask: String,
        closeTime: OffsetDateTime
    ) = KalshiMarket(
        ticker = "T$strike",
        yesSubTitle = "Above $strike",
        closeTime = closeTime,
        yesBidDollars = BigDecimal(bid),
        yesAskDollars = BigDecimal(ask),
        strikeType = "greater",
        floorStrike = BigDecimal(strike)
    )

    private fun stubEvent(
        series: String,
        eventTicker: String,
        markets: List<KalshiMarket>
    ) {
        whenever(gateway.getEvents(series)).thenReturn(KalshiEventsResponse(listOf(KalshiEvent(eventTicker, series))))
        whenever(gateway.getMarkets(eventTicker)).thenReturn(KalshiMarketsResponse(markets))
    }

    private fun EconomicCalendarResponse.print(indicator: String) = recent.single { it.indicator == indicator }

    private fun EconomicCalendarResponse.release(indicator: String) = upcoming.single { it.indicator == indicator }

    @Test
    fun `payrolls print is the monthly change in thousands with the prior month's change`() {
        stubFred(payems = monthly("2026-10" to "159293", "2026-09" to "159128", "2026-08" to "159068"))
        noKalshiEvents()

        val payrolls = service.getCalendar().print("NONFARM_PAYROLLS")

        assertThat(payrolls.period).isEqualTo(LocalDate.of(2026, 10, 1))
        assertThat(payrolls.unit).isEqualTo("thousands")
        assertThat(payrolls.value).isEqualByComparingTo("165")
        assertThat(payrolls.prior).isEqualByComparingTo("60")
    }

    @Test
    fun `unemployment print is the latest rate with the prior month's rate`() {
        stubFred(unrate = monthly("2026-10" to "4.1", "2026-09" to "4.0"))
        noKalshiEvents()

        val unemployment = service.getCalendar().print("UNEMPLOYMENT_RATE")

        assertThat(unemployment.unit).isEqualTo("%")
        assertThat(unemployment.value).isEqualByComparingTo("4.1")
        assertThat(unemployment.prior).isEqualByComparingTo("4.0")
    }

    @Test
    fun `CPI month-on-month is the percent change in the seasonally adjusted index`() {
        stubFred(cpiSa = monthly("2026-09" to "336.298", "2026-08" to "334.131", "2026-07" to "333.000"))
        noKalshiEvents()

        val cpiMom = service.getCalendar().print("CPI_MOM")

        assertThat(cpiMom.period).isEqualTo(LocalDate.of(2026, 9, 1))
        assertThat(cpiMom.value).isEqualByComparingTo("0.65")
        assertThat(cpiMom.prior).isEqualByComparingTo("0.34")
    }

    @Test
    fun `CPI and core CPI year-on-year compare the unadjusted index with the same month a year earlier`() {
        val nsa =
            monthly(
                "2026-09" to "330.0",
                "2026-08" to "329.0",
                "2025-09" to "320.0",
                "2025-08" to "319.5"
            )
        stubFred(cpiNsa = nsa, coreNsa = nsa)
        noKalshiEvents()

        val calendar = service.getCalendar()

        for (indicator in listOf("CPI_YOY", "CORE_CPI_YOY")) {
            val yoy = calendar.print(indicator)
            assertThat(yoy.period).isEqualTo(LocalDate.of(2026, 9, 1))
            assertThat(yoy.value).isEqualByComparingTo("3.13")
            assertThat(yoy.prior).isEqualByComparingTo("2.97")
        }
    }

    @Test
    fun `a series that fails upstream is omitted while the others are still returned`() {
        stubFred(unrate = monthly("2026-10" to "4.1", "2026-09" to "4.0"))
        whenever(fetcher.fetch("PAYEMS")).thenThrow(RuntimeException("FRED down"))
        noKalshiEvents()

        val calendar = service.getCalendar()

        assertThat(calendar.recent.map { it.indicator }).containsExactly("UNEMPLOYMENT_RATE")
    }

    @Test
    fun `upcoming CPI release carries the market-implied median from the above-X ladder`() {
        stubFred()
        noKalshiEvents()
        val close = OffsetDateTime.of(2026, 12, 10, 13, 29, 0, 0, ZoneOffset.UTC)
        stubEvent(
            "KXCPI",
            "KXCPI-26NOV",
            listOf(
                above("0.1", "0.15", "0.19", close),
                above("-0.1", "0.57", "0.61", close),
                above("0.0", "0.26", "0.30", close)
            )
        )

        val cpi = service.getCalendar().release("CPI_MOM")

        assertThat(cpi.releaseDate).isEqualTo(LocalDate.of(2026, 12, 10))
        assertThat(cpi.period).isEqualTo(LocalDate.of(2026, 11, 1))
        assertThat(cpi.unit).isEqualTo("%")
        // P(>-0.1)=0.59, P(>0.0)=0.28 → 0.5 crossed 9/31 of the way from -0.1 to 0.0.
        assertThat(cpi.expected).isEqualByComparingTo("-0.07")
    }

    @Test
    fun `upcoming payrolls expectation is scaled to thousands to match the recent print`() {
        stubFred()
        noKalshiEvents()
        val close = OffsetDateTime.of(2026, 12, 4, 13, 29, 0, 0, ZoneOffset.UTC)
        stubEvent(
            "KXPAYROLLS",
            "KXPAYROLLS-26NOV",
            listOf(
                above("70000", "0.55", "0.59", close),
                above("80000", "0.49", "0.52", close),
                above("90000", "0.43", "0.48", close)
            )
        )

        val payrolls = service.getCalendar().release("NONFARM_PAYROLLS")

        assertThat(payrolls.unit).isEqualTo("thousands")
        assertThat(payrolls.expected).isEqualByComparingTo("81")
    }

    @Test
    fun `release is still listed without an expectation when the ladder never crosses 50 percent`() {
        stubFred()
        noKalshiEvents()
        val close = OffsetDateTime.of(2026, 12, 4, 13, 29, 0, 0, ZoneOffset.UTC)
        stubEvent(
            "KXU3",
            "KXU3-26NOV",
            listOf(above("4.0", "0.80", "0.84", close), above("4.1", "0.60", "0.64", close))
        )

        val unemployment = service.getCalendar().release("UNEMPLOYMENT_RATE")

        assertThat(unemployment.releaseDate).isEqualTo(LocalDate.of(2026, 12, 4))
        assertThat(unemployment.expected).isNull()
    }

    @Test
    fun `release date is the US Eastern calendar date of the release`() {
        stubFred()
        noKalshiEvents()
        // 02:00Z on the 5th is still the evening of the 4th in New York.
        val close = OffsetDateTime.of(2026, 12, 5, 2, 0, 0, 0, ZoneOffset.UTC)
        stubEvent("KXU3", "KXU3-26NOV", listOf(above("4.0", "0.80", "0.84", close)))

        assertThat(service.getCalendar().release("UNEMPLOYMENT_RATE").releaseDate)
            .isEqualTo(LocalDate.of(2026, 12, 4))
    }

    @Test
    fun `picks the nearest open event whose release is still in the future`() {
        stubFred()
        noKalshiEvents()
        val past = OffsetDateTime.now().minusHours(2)
        val next = OffsetDateTime.now().plusDays(30)
        val later = OffsetDateTime.now().plusDays(60)
        whenever(gateway.getEvents("KXCPIYOY")).thenReturn(
            KalshiEventsResponse(
                listOf(
                    KalshiEvent("KXCPIYOY-26OCT", "CPI YoY in October"),
                    KalshiEvent("KXCPIYOY-26DEC", "CPI YoY in December"),
                    KalshiEvent("KXCPIYOY-26NOV", "CPI YoY in November")
                )
            )
        )
        whenever(gateway.getMarkets("KXCPIYOY-26OCT")).thenReturn(
            KalshiMarketsResponse(listOf(above("3.0", "0.50", "0.52", past)))
        )
        whenever(gateway.getMarkets("KXCPIYOY-26DEC")).thenReturn(
            KalshiMarketsResponse(listOf(above("3.0", "0.50", "0.52", later)))
        )
        whenever(gateway.getMarkets("KXCPIYOY-26NOV")).thenReturn(
            KalshiMarketsResponse(listOf(above("3.0", "0.50", "0.52", next)))
        )

        val cpiYoy = service.getCalendar().release("CPI_YOY")

        assertThat(cpiYoy.period).isEqualTo(LocalDate.of(2026, 11, 1))
    }

    @Test
    fun `next Fed decision is listed by date with no expectation`() {
        stubFred()
        noKalshiEvents()
        val close = OffsetDateTime.of(2026, 12, 9, 18, 59, 0, 0, ZoneOffset.UTC)
        stubEvent(
            "KXFEDDECISION",
            "KXFEDDECISION-26DEC",
            listOf(KalshiMarket(ticker = "H0", yesSubTitle = "Hold", closeTime = close))
        )

        val fed = service.getCalendar().release("FOMC_DECISION")

        assertThat(fed.releaseDate).isEqualTo(LocalDate.of(2026, 12, 9))
        assertThat(fed.expected).isNull()
        assertThat(fed.period).isNull()
    }

    @Test
    fun `a failing upcoming leg does not remove recent prints or other releases`() {
        stubFred(unrate = monthly("2026-10" to "4.1", "2026-09" to "4.0"))
        noKalshiEvents()
        whenever(gateway.getEvents("KXCPI")).thenThrow(RuntimeException("Kalshi down"))
        val close = OffsetDateTime.of(2026, 12, 4, 13, 29, 0, 0, ZoneOffset.UTC)
        stubEvent("KXU3", "KXU3-26NOV", listOf(above("4.0", "0.80", "0.84", close)))

        val calendar = service.getCalendar()

        assertThat(calendar.recent.map { it.indicator }).containsExactly("UNEMPLOYMENT_RATE")
        assertThat(calendar.upcoming.map { it.indicator }).containsExactly("UNEMPLOYMENT_RATE")
    }

    @Test
    fun `upcoming releases are ordered soonest first and the response is US scoped`() {
        stubFred()
        noKalshiEvents()
        stubEvent(
            "KXCPI",
            "KXCPI-26NOV",
            listOf(above("0.0", "0.5", "0.5", OffsetDateTime.of(2026, 12, 10, 13, 29, 0, 0, ZoneOffset.UTC)))
        )
        stubEvent(
            "KXU3",
            "KXU3-26NOV",
            listOf(above("4.0", "0.5", "0.5", OffsetDateTime.of(2026, 12, 4, 13, 29, 0, 0, ZoneOffset.UTC)))
        )

        val calendar = service.getCalendar()

        assertThat(calendar.country).isEqualTo("US")
        assertThat(calendar.asOf).isEqualTo(LocalDate.of(2026, 11, 10))
        assertThat(calendar.upcoming.map { it.indicator }).containsExactly("UNEMPLOYMENT_RATE", "CPI_MOM")
    }
}