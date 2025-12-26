package com.beancounter.common

import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
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
        val sgtWednesday =
            LocalDateTime.of(
                2023,
                7,
                11,
                13,
                56
            )
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to Tuesday as previous days close
        assertThat(
            previousClose.getPriceDate(
                morningOf,
                nasdaq,
                true
            )
        ).isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_previousDayOnCurrentTradingDay() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday =
            LocalDateTime.of(
                2023,
                7,
                11,
                2,
                15
            )
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to Tuesday as previous days close
        assertThat(
            previousClose.getPriceDate(
                morningOf,
                nasdaq,
                true
            )
        ).isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_requestDateReturnedForNonCurrentMode() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday =
            LocalDateTime.of(
                2023,
                7,
                10,
                2,
                15
            )
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to same date as requested when it's in the past
        // Need to assess if this is legit - suggests caller date is always a literal date, not the
        //  market datetime
        assertThat(
            previousClose.getPriceDate(
                morningOf,
                nasdaq,
                false
            )
        ).isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close

        assertThat(
            previousClose.getPriceDate(
                morningOf,
                nasdaq
            )
        ).isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_todayOnMarketsClosed() {
        // Wednesday 2am SGT/Tues GMT
        val sgtWednesday =
            LocalDateTime.of(
                2023,
                7,
                11,
                7,
                15
            )
        val morningOf = sgtWednesday.atZone(dateUtils.zoneId)
        // Should resolve to Tuesday as previous days close
        assertThat(
            previousClose.getPriceDate(
                morningOf,
                nasdaq,
                true
            )
        ).isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun is_PricesAvailableAtMarketClose() {
        // Market close on Monday ETC
        val asAtDate =
            OffsetDateTime
                .ofInstant(
                    LocalDateTime
                        .of(
                            2023,
                            7,
                            10,
                            23,
                            0
                        ).toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC
                ).toZonedDateTime()

        // Prices Now available.
        assertThat(
            previousClose.getPriceDate(
                asAtDate,
                nasdaq,
                true
            )
        ).isEqualTo(dateUtils.getFormattedDate(EXPECTED_DATE)) // Nasdaq last close
    }

    @Test
    fun doesSundayReturnFriday() {
        val asAtDate =
            OffsetDateTime
                .ofInstant(
                    // Sunday
                    LocalDateTime
                        .of(
                            2023,
                            7,
                            9,
                            15,
                            0
                        ).toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC
                ).toZonedDateTime()
        // Resolve to Friday
        assertThat(
            previousClose.getPriceDate(
                asAtDate,
                nasdaq,
                false
            )
        ).isEqualTo(dateUtils.getFormattedDate("2023-07-07")) // Nasdaq last close
    }

    @Test
    fun is_MarketDateCalculatedAsYesterdayWhenToday() {
        val todayUtils = DateUtils()

        val today = todayUtils.date

        // 2pm ECT
        val anyUnavailableUTCDateTime =
            OffsetDateTime
                .ofInstant(
                    LocalDateTime
                        .of(
                            today.year,
                            today.month,
                            today.dayOfMonth,
                            18,
                            0
                        ).toInstant(ZoneOffset.UTC),
                    ZoneOffset.UTC
                ).toZonedDateTime()

        // Requesting today, but prices are not yet available.
        val todayNoPrices =
            previousClose.getPriceDate(
                anyUnavailableUTCDateTime,
                nasdaq,
                true
            )

        // Calculate expected date by going back to the last trading day
        var expectedDate = today.minusDays(1)
        while (!previousClose.isTradingDay(expectedDate.atStartOfDay(ZoneId.of("UTC")))) {
            expectedDate = expectedDate.minusDays(1)
        }

        assertThat(todayNoPrices).isEqualTo(expectedDate)
    }

    @Test
    fun is_WeekendFound() {
        assertThat(previousClose.isTradingDay(friday)).isTrue // Friday
        assertThat(previousClose.isTradingDay(saturday)).isFalse // Saturday
        assertThat(previousClose.isTradingDay(sunday)).isFalse // Sunday
        assertThat(previousClose.isTradingDay(monday)).isTrue // Monday
    }

    // Test data for weekend trading day tests
    private val friday: ZonedDateTime = ZonedDateTime.of(2019, 10, 18, 0, 0, 0, 0, ZoneId.of("UTC"))
    private val saturday: ZonedDateTime = ZonedDateTime.of(2019, 10, 19, 0, 0, 0, 0, ZoneId.of("UTC"))
    private val sunday: ZonedDateTime = ZonedDateTime.of(2019, 10, 20, 0, 0, 0, 0, ZoneId.of("UTC"))
    private val monday: ZonedDateTime = ZonedDateTime.of(2019, 10, 21, 0, 0, 0, 0, ZoneId.of("UTC"))

    @Test
    fun `Christmas is not a trading day`() {
        // Christmas 2024 falls on Wednesday
        val christmas2024 = ZonedDateTime.of(2024, 12, 25, 12, 0, 0, 0, ZoneId.of("UTC"))
        assertThat(previousClose.isTradingDay(christmas2024)).isFalse
    }

    @Test
    fun `New Years Day is not a trading day`() {
        val newYears2025 = ZonedDateTime.of(2025, 1, 1, 12, 0, 0, 0, ZoneId.of("UTC"))
        assertThat(previousClose.isTradingDay(newYears2025)).isFalse
    }

    @Test
    fun `Independence Day is not a trading day`() {
        val july4th2024 = ZonedDateTime.of(2024, 7, 4, 12, 0, 0, 0, ZoneId.of("UTC"))
        assertThat(previousClose.isTradingDay(july4th2024)).isFalse
    }

    @Test
    fun `day after Christmas should resolve to Christmas Eve when requesting previous close`() {
        // Dec 26, 2024 - day after Christmas, before market close time
        val dec26Morning = ZonedDateTime.of(2024, 12, 26, 10, 0, 0, 0, ZoneId.of("America/New_York"))

        // When requesting current prices before market close, should resolve to previous trading day
        // Dec 25 is Christmas (holiday), so should resolve to Dec 24 (Christmas Eve)
        val resolvedDate = previousClose.getPriceDate(dec26Morning, nasdaq, true)
        assertThat(resolvedDate).isEqualTo(dateUtils.getFormattedDate("2024-12-24"))
    }
}