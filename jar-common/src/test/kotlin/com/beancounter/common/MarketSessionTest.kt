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
 * A market is in session, and worth a live quote, from its `openTime` until its `priceTime`
 * on a trading day, both on the market's own clock. Outside that window the stored close is
 * as fresh as any quote.
 */
internal class MarketSessionTest {
    private val previousClose = PreviousClosePriceDate(DateUtils("Asia/Singapore"))

    private val nasdaq = Market(code = "NASDAQ", priceTime = LocalTime.of(16, 30))

    private val lon =
        Market(
            code = "LON",
            currencyId = "GBP",
            timezoneId = "Europe/London",
            openTime = LocalTime.of(8, 0),
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
    fun `should be in session during New York trading hours`() {
        // 2023-07-10 (Mon) 15:00 UTC == 11:00 EDT.
        assertThat(previousClose.isInSession(utc(2023, 7, 10, 15), nasdaq)).isTrue()
    }

    @Test
    fun `should not be in session before the open`() {
        // 2023-07-10 (Mon) 13:00 UTC == 09:00 EDT, before the 09:30 open.
        assertThat(previousClose.isInSession(utc(2023, 7, 10, 13), nasdaq)).isFalse()
    }

    @Test
    fun `should not be in session once the close is available`() {
        // 2023-07-10 (Mon) 20:30 UTC == 16:30 EDT, priceTime.
        assertThat(previousClose.isInSession(utc(2023, 7, 10, 20, 30), nasdaq)).isFalse()
    }

    @Test
    fun `should not be in session at the weekend`() {
        // 2023-07-08 (Sat) 15:00 UTC == 11:00 EDT.
        assertThat(previousClose.isInSession(utc(2023, 7, 8, 15), nasdaq)).isFalse()
    }

    @Test
    fun `should not be in session on a market holiday`() {
        // 2023-07-04 (Tue, Independence Day) 15:00 UTC == 11:00 EDT.
        assertThat(previousClose.isInSession(utc(2023, 7, 4, 15), nasdaq)).isFalse()
    }

    @Test
    fun `should use the market's own open time and zone`() {
        // 2023-07-10 (Mon) 07:30 UTC == 08:30 BST: London open, New York not.
        assertThat(previousClose.isInSession(utc(2023, 7, 10, 7, 30), lon)).isTrue()
        assertThat(previousClose.isInSession(utc(2023, 7, 10, 7, 30), nasdaq)).isFalse()
    }
}