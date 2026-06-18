package com.beancounter.common

import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Specifies the DESIRED contract for close-price availability:
 *
 *   `market.priceTime` is a wall-clock time in the MARKET's own timezone.
 *   A market's close price for date D is "available" once the market-local
 *   clock reaches priceTime on D — independent of the request's timezone
 *   or the server's zone.
 *
 * These tests are expected to FAIL against the current implementation, which
 * anchors priceTime to the request's UTC offset rather than the market zone.
 */
internal class MarketCloseAvailabilityTest {
    private val dateUtils = DateUtils("Asia/Singapore")
    private val previousClose = PreviousClosePriceDate(dateUtils)

    // NASDAQ defaults: timezoneId = US/Eastern, priceTime = 19:00.
    private val nasdaq = Market("NASDAQ")

    // LON trades in London; priceTime 19:00 London (EODHD EOD publish window).
    private val lon =
        Market(
            code = "LON",
            currencyId = "GBP",
            timezoneId = "Europe/London",
            priceTime = LocalTime.of(19, 0)
        )

    private fun utc(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0
    ): ZonedDateTime =
        OffsetDateTime
            .of(year, month, day, hour, minute, 0, 0, ZoneOffset.UTC)
            .toZonedDateTime()

    @Test
    fun `nasdaq close NOT available at 19_00 UTC because that is 15_00 New York`() {
        // 2023-07-10 (Mon) 19:00 UTC == 15:00 EDT — NASDAQ has not yet closed.
        // Desired: fall back to the prior trading day (Fri 2023-07-07).
        val resolved = previousClose.getPriceDate(utc(2023, 7, 10, 19), nasdaq, true)
        assertThat(resolved).isEqualTo(dateUtils.getFormattedDate("2023-07-07"))
    }

    @Test
    fun `nasdaq close IS available at 23_00 UTC because that is 19_00 New York`() {
        // 2023-07-10 (Mon) 23:00 UTC == 19:00 EDT — at priceTime, today's close.
        val resolved = previousClose.getPriceDate(utc(2023, 7, 10, 23), nasdaq, true)
        assertThat(resolved).isEqualTo(dateUtils.getFormattedDate("2023-07-10"))
    }

    @Test
    fun `lon close IS available at 18_00 UTC in summer because that is 19_00 London`() {
        // 2023-07-10 (Mon) 18:00 UTC == 19:00 BST — LSE close published, today.
        val resolved = previousClose.getPriceDate(utc(2023, 7, 10, 18), lon, true)
        assertThat(resolved).isEqualTo(dateUtils.getFormattedDate("2023-07-10"))
    }

    @Test
    fun `lon close NOT available at 17_00 UTC in summer because that is 18_00 London`() {
        // 2023-07-10 (Mon) 17:00 UTC == 18:00 BST — before priceTime; prior close.
        val resolved = previousClose.getPriceDate(utc(2023, 7, 10, 17), lon, true)
        assertThat(resolved).isEqualTo(dateUtils.getFormattedDate("2023-07-07"))
    }
}