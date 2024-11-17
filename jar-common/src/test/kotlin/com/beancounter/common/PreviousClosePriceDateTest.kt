package com.beancounter.common

import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

private const val EXPECTED_DATE = "2023-07-10"

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
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to Tuesday as previous days close
        assertThat(previousClose.getPriceDate(morningOf, nasdaq, true))
            .isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_previousDayOnCurrentTradingDay() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday = LocalDateTime.of(2023, 7, 11, 2, 15)
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to Tuesday as previous days close
        assertThat(previousClose.getPriceDate(morningOf, nasdaq, true))
            .isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_requestDateReturnedForNonCurrentMode() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday = LocalDateTime.of(2023, 7, 10, 2, 15)
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to same date as requested when it's in the past
        // Need to assess if this is legit - suggests caller date is always a literal date, not the
        //  market datetime
        assertThat(previousClose.getPriceDate(morningOf, nasdaq, false))
            .isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close

        assertThat(previousClose.getPriceDate(morningOf, nasdaq))
            .isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_todayOnMarketsClosed() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday = LocalDateTime.of(2023, 7, 11, 7, 15)
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to Tuesday as previous days close
        assertThat(previousClose.getPriceDate(morningOf, nasdaq, true))
            .isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_PricesAvailableAtMarketClose() {
        // Market close on Monday ETC
        val asAtDate =
            OffsetDateTime.ofInstant(
                LocalDateTime.of(2023, 7, 10, 23, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC,
            ).toZonedDateTime()

        // Prices Now available.
        assertThat(previousClose.getPriceDate(asAtDate, nasdaq, true))
            .isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun doesSundayReturnFriday() {
        val asAtDate =
            OffsetDateTime.ofInstant(
                // Sunday
                LocalDateTime.of(2023, 7, 9, 15, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC,
            ).toZonedDateTime()
        // Resolve to Friday
        assertThat(previousClose.getPriceDate(asAtDate, nasdaq, false))
            .isEqualTo(dateUtils.getFormattedDate("2023-07-07")) // Nasdaq last close
    }

    @Test
    fun is_MarketDateCalculatedAsYesterdayWhenToday() {
        val todayUtils = DateUtils()

        val today = todayUtils.date

        // 2pm ECT
        val anyUnavailableUTCDateTime =
            OffsetDateTime.ofInstant(
                LocalDateTime.of(today.year, today.month, today.dayOfMonth, 18, 0).toInstant(ZoneOffset.UTC),
                ZoneOffset.UTC,
            ).toZonedDateTime()

        // Requesting today, but prices are not yet available.
        val todayNoPrices = previousClose.getPriceDate(anyUnavailableUTCDateTime, nasdaq, true)
        val deduct =
            when (today.dayOfWeek) {
                DayOfWeek.MONDAY -> {
                    3L // Close of business Friday
                }

                DayOfWeek.SUNDAY -> {
                    2L // CoB Friday
                }

                else -> {
                    1L // CoB Yesterday
                }
            }
        assertThat(todayNoPrices).isEqualTo(today.minusDays(deduct))
    }

    @Test
    fun is_WeekendFound() {
        assertThat(previousClose.isTradingDay(friday)).isTrue // Friday
        assertThat(previousClose.isTradingDay(saturday)).isFalse // Saturday
        assertThat(previousClose.isTradingDay(sunday)).isFalse // Sunday
        assertThat(previousClose.isTradingDay(monday)).isTrue // Monday
    }

    // "2019-10-18"
    private val friday: ZonedDateTime =
        ZonedDateTime.ofInstant(
            LocalDateTime.of(2019, 10, 18, 0, 0).toInstant(ZoneOffset.UTC),
            ZoneId.of("UTC"),
        )

    private val sunday: ZonedDateTime =
        ZonedDateTime.ofInstant(
            LocalDateTime.of(2019, 10, 20, 0, 0).toInstant(ZoneOffset.UTC),
            ZoneId.of("UTC"),
        )

    private val saturday: ZonedDateTime =
        ZonedDateTime.ofInstant(
            LocalDateTime.of(2019, 10, 19, 0, 0).toInstant(ZoneOffset.UTC),
            ZoneId.of("UTC"),
        )

    private val monday: ZonedDateTime =
        ZonedDateTime.ofInstant(
            LocalDateTime.of(2019, 10, 21, 0, 0).toInstant(ZoneOffset.UTC),
            ZoneId.of("UTC"),
        )

    //    private val sunday: LocalDateTime get() = LocalDateTime.of(dateUtils.getDate("2019-10-20"), LocalTime.MIDNIGHT)
}
