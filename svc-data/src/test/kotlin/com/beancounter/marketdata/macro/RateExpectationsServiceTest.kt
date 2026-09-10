package com.beancounter.marketdata.macro

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime

/**
 * Unit tests for [RateExpectationsService] with [KalshiGateway] and [MacroObservationRepo]
 * mocked. Pins: nearest-future-event selection, the bid/ask-mid → last-price → skip probability
 * fallback chain, outcome ordering, and the trend-omitted-without-history contract.
 */
class RateExpectationsServiceTest {
    private val gateway = mock<KalshiGateway>()
    private val observationRepo = mock<MacroObservationRepo>()
    private val service = RateExpectationsService(gateway, observationRepo)

    private fun market(
        ticker: String,
        yesSubTitle: String?,
        closeTime: OffsetDateTime,
        bid: BigDecimal? = null,
        ask: BigDecimal? = null,
        lastPrice: BigDecimal? = null,
        volume: BigDecimal? = null,
        subtitle: String? = null
    ): KalshiMarket =
        KalshiMarket(
            ticker = ticker,
            yesSubTitle = yesSubTitle,
            subtitle = subtitle,
            closeTime = closeTime,
            lastPriceDollars = lastPrice,
            yesBidDollars = bid,
            yesAskDollars = ask,
            volume24hFp = volume
        )

    @Test
    fun `picks the open event with the nearest future close time`() {
        val near = OffsetDateTime.now().plusDays(30)
        val far = OffsetDateTime.now().plusDays(60)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(
                listOf(
                    KalshiEvent("KXFEDDECISION-26NOV", "Fed decision November"),
                    KalshiEvent("KXFEDDECISION-26SEP", "Fed decision September")
                )
            )
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26NOV")).thenReturn(
            KalshiMarketsResponse(listOf(market("t1", "Hold", far, bid = BigDecimal("0.80"), ask = BigDecimal("0.82"))))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(market("t2", "Hold", near, bid = BigDecimal("0.70"), ask = BigDecimal("0.72")))
            )
        )

        val result = service.getRateExpectations()

        assertThat(result).isNotNull
        assertThat(result!!.event).isEqualTo("KXFEDDECISION-26SEP")
        assertThat(result.title).isEqualTo("Fed decision September")
        assertThat(result.closeTime).isEqualTo(near)
    }

    @Test
    fun `probability is the mid of yes bid and ask when both are present`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(market("t1", "Cut 25bps", closeTime, bid = BigDecimal("0.62"), ask = BigDecimal("0.65")))
            )
        )

        val result = service.getRateExpectations()

        assertThat(result!!.outcomes.first().probability).isEqualByComparingTo(BigDecimal("0.635"))
    }

    @Test
    fun `probability falls back to last price when bid or ask is missing`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(
                    market(
                        "t1",
                        "Cut 25bps",
                        closeTime,
                        bid = BigDecimal("0.62"),
                        ask = null,
                        lastPrice = BigDecimal("0.60")
                    )
                )
            )
        )

        val result = service.getRateExpectations()

        assertThat(result!!.outcomes.first().probability).isEqualByComparingTo(BigDecimal("0.60"))
    }

    @Test
    fun `an outcome with no bid, ask or last price is skipped`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(
                    market("t1", "No quotes yet", closeTime),
                    market("t2", "Hold", closeTime, bid = BigDecimal("0.50"), ask = BigDecimal("0.52"))
                )
            )
        )

        val result = service.getRateExpectations()

        assertThat(result!!.outcomes).hasSize(1)
        assertThat(result.outcomes.first().label).isEqualTo("Hold")
    }

    @Test
    fun `label falls back to subtitle when yes_sub_title is blank`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(
                    market(
                        "t1",
                        yesSubTitle = null,
                        closeTime = closeTime,
                        bid = BigDecimal("0.50"),
                        ask = BigDecimal("0.52"),
                        subtitle = "Fallback label"
                    )
                )
            )
        )

        val result = service.getRateExpectations()

        assertThat(result!!.outcomes.first().label).isEqualTo("Fallback label")
    }

    @Test
    fun `outcomes are sorted by probability descending`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(
                    market("t1", "Hold", closeTime, bid = BigDecimal("0.10"), ask = BigDecimal("0.10")),
                    market("t2", "Cut 25bps", closeTime, bid = BigDecimal("0.60"), ask = BigDecimal("0.66")),
                    market("t3", "Cut 50bps", closeTime, bid = BigDecimal("0.20"), ask = BigDecimal("0.24"))
                )
            )
        )

        val result = service.getRateExpectations()

        assertThat(result!!.outcomes.map { it.label }).containsExactly("Cut 25bps", "Cut 50bps", "Hold")
    }

    @Test
    fun `returns null when there are no open events`() {
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(KalshiEventsResponse(emptyList()))

        val result = service.getRateExpectations()

        assertThat(result).isNull()
    }

    @Test
    fun `returns null when no event has a future close time`() {
        val past = OffsetDateTime.now().minusDays(1)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26AUG", "Fed decision August")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26AUG")).thenReturn(
            KalshiMarketsResponse(listOf(market("t1", "Hold", past, bid = BigDecimal("0.9"), ask = BigDecimal("0.91"))))
        )

        val result = service.getRateExpectations()

        assertThat(result).isNull()
    }

    @Test
    fun `trend is omitted when there is no prior observation`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(market("t1", "Hold", closeTime, bid = BigDecimal("0.50"), ask = BigDecimal("0.52")))
            )
        )
        whenever(
            observationRepo.findFirstBySeriesAndMetricAndObservedAtLessThanEqualOrderByObservedAtDesc(
                any(),
                any(),
                any()
            )
        ).thenReturn(null)

        val result = service.getRateExpectations()

        assertThat(result!!.trend).isNull()
    }

    @Test
    fun `trend compares against the nearest observation at least 6 days old`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(market("t1", "Hold", closeTime, bid = BigDecimal("0.62"), ask = BigDecimal("0.64")))
            )
        )
        val priorObservedAt = LocalDateTime.now().minusDays(7)
        whenever(
            observationRepo.findFirstBySeriesAndMetricAndObservedAtLessThanEqualOrderByObservedAtDesc(
                eq("KALSHI:KXFEDDECISION-26SEP"),
                eq("Hold"),
                any()
            )
        ).thenReturn(MacroObservation("KALSHI:KXFEDDECISION-26SEP", "Hold", BigDecimal("0.42"), priorObservedAt))

        val result = service.getRateExpectations()

        assertThat(result!!.trend).hasSize(1)
        val trend = result.trend!!.first()
        assertThat(trend.label).isEqualTo("Hold")
        assertThat(trend.probabilityPrior).isEqualByComparingTo(BigDecimal("0.42"))
        assertThat(trend.observedAt).isEqualTo(priorObservedAt)
        // 0.63 (mid of 0.62/0.64) - 0.42 = 0.21
        assertThat(trend.change).isEqualByComparingTo(BigDecimal("0.21"))
    }

    @Test
    fun `snapshot persists one macro_observation row per outcome`() {
        val closeTime = OffsetDateTime.now().plusDays(10)
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(
            KalshiEventsResponse(listOf(KalshiEvent("KXFEDDECISION-26SEP", "Fed decision")))
        )
        whenever(gateway.getMarkets("KXFEDDECISION-26SEP")).thenReturn(
            KalshiMarketsResponse(
                listOf(
                    market("t1", "Hold", closeTime, bid = BigDecimal("0.50"), ask = BigDecimal("0.52")),
                    market("t2", "Cut 25bps", closeTime, bid = BigDecimal("0.40"), ask = BigDecimal("0.44"))
                )
            )
        )

        service.snapshot()

        val captor = argumentCaptor<MacroObservation>()
        verify(observationRepo, times(2)).save(captor.capture())
        assertThat(captor.allValues.map { it.series }).allMatch { it == "KALSHI:KXFEDDECISION-26SEP" }
        assertThat(captor.allValues.map { it.metric }).containsExactlyInAnyOrder("Hold", "Cut 25bps")
    }

    @Test
    fun `snapshot does nothing when there is no current event to sample`() {
        whenever(gateway.getEvents("KXFEDDECISION")).thenReturn(KalshiEventsResponse(emptyList()))

        service.snapshot()

        verify(observationRepo, org.mockito.kotlin.never()).save(any<MacroObservation>())
    }
}