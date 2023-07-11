package com.beancounter.common

import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/**
 * BC values on the previous days close. These facts are asserted in this class
 */
internal class PreviousClosePriceDateTest {
    // System default timezone
    private var dateUtils = DateUtils("Asia/Singapore")
    private var previousClose = PreviousClosePriceDate(dateUtils)
    private val nasdaq = Market("NASDAQ")

    @Test
    fun why_this() {
        val sgtWednesday = LocalDateTime.of(2023, 7, 11, 13, 56)
        val morningOf = sgtWednesday.atZone(dateUtils.getZoneId())
        // Should resolve to Tuesday as previous days close
        assertThat(previousClose.getPriceDate(morningOf.toOffsetDateTime(), nasdaq, true))
            .isEqualTo(dateUtils.getDate("2023-07-10")) // Nasdaq last close
    }

    @Test
    fun is_previousDayOnCurrentTradingDay() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday = LocalDateTime.of(2023, 7, 11, 2, 15)
        val morningOf = sgtWednesday.atZone(dateUtils.getZoneId())
        // Should resolve to Tuesday as previous days close
        assertThat(previousClose.getPriceDate(morningOf.toOffsetDateTime(), nasdaq, true))
            .isEqualTo(dateUtils.getDate("2023-07-10")) // Nasdaq last close
    }

    @Test
    fun is_requestDateReturnedForNonCurrentMode() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday = LocalDateTime.of(2023, 7, 10, 2, 15)
        val morningOf = sgtWednesday.atZone(dateUtils.getZoneId())
        // Should resolve to same date as requested when it's in the past
        // Need to assess if this is legit - suggests caller date is always a literal date, not the
        //  market datetime
        assertThat(previousClose.getPriceDate(morningOf.toOffsetDateTime(), nasdaq, false))
            .isEqualTo(dateUtils.getDate("2023-07-10")) // Nasdaq last close

        assertThat(previousClose.getPriceDate(morningOf.toOffsetDateTime(), nasdaq))
            .isEqualTo(dateUtils.getDate("2023-07-10")) // Nasdaq last close
    }

    @Test
    fun is_todayOnMarketsClosed() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday = LocalDateTime.of(2023, 7, 11, 7, 15)
        val morningOf = sgtWednesday.atZone(dateUtils.getZoneId())
        // Should resolve to Tuesday as previous days close
        assertThat(previousClose.getPriceDate(morningOf.toOffsetDateTime(), nasdaq, true))
            .isEqualTo(dateUtils.getDate("2023-07-10")) // Nasdaq last close
    }

    @Test
    fun is_PricesAvailableAtMarketClose() {
        // Market close on Monday ETC
        val asAtDate = OffsetDateTime.ofInstant(
            LocalDateTime.of(2023, 7, 10, 23, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        // Prices Now available.
        assertThat(previousClose.getPriceDate(asAtDate, nasdaq, true))
            .isEqualTo(dateUtils.getDate("2023-07-10")) // Nasdaq last close
    }

    @Test
    fun is_WeekendAccounted() {
        // Monday UTC, SUNDAY SGT
        val asAtDate = OffsetDateTime.ofInstant(
            LocalDateTime.of(2023, 7, 9, 15, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC,
        )
        // Resolve to Friday
        assertThat(previousClose.getPriceDate(asAtDate, nasdaq, false))
            .isEqualTo(dateUtils.getDate("2023-07-07")) // Nasdaq last close
    }

    @Test
    fun is_MarketDateCalculatedAsYesterdayWhenToday() {
        val todayUtils = DateUtils()

        val today = todayUtils.date

        // 2pm ECT
        val pricesUnavailable = OffsetDateTime.ofInstant(
            LocalDateTime.of(today.year, today.month, today.dayOfMonth, 18, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC,
        )

        // Requesting today, but prices are not yet available.
        val todayNoPrices = previousClose.getPriceDate(pricesUnavailable, nasdaq, true)
        assertThat(todayNoPrices).isEqualTo(today.minusDays(1))
    }

    @Test
    fun is_WeekendFound() {
        assertThat(previousClose.isTradingDay(sunday)).isFalse // Sunday
        assertThat(previousClose.isTradingDay(saturday)).isFalse // Saturday
        assertThat(previousClose.isTradingDay(friday)).isTrue // Friday
        assertThat(previousClose.isTradingDay(monday)).isTrue // Monday
    }

    private val friday: LocalDateTime get() = LocalDateTime.of(dateUtils.getDate("2019-10-18"), LocalTime.MIDNIGHT)
    private val sunday: LocalDateTime get() = LocalDateTime.of(dateUtils.getDate("2019-10-20"), LocalTime.MIDNIGHT)
    private val saturday: LocalDateTime get() = LocalDateTime.of(dateUtils.getDate("2019-10-19"), LocalTime.MIDNIGHT)
    private val monday: LocalDateTime get() = LocalDateTime.of(dateUtils.getDate("2019-10-21"), LocalTime.MIDNIGHT)
}
