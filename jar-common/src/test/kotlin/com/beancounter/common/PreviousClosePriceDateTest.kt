package com.beancounter.common

import com.beancounter.common.TestMarkets.Companion.USD
import com.beancounter.common.contracts.PriceRequest
import com.beancounter.common.model.Market
import com.beancounter.common.utils.AssetUtils
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
    private val message = "close: {} localDateTime: {}"

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(PreviousClosePriceDateTest::class.java)
    }

    @Test
    fun is_MarketDateCalculated() {
        val nasdaqClose = dateUtils.getDate("2020-07-17", dateUtils.getZoneId())
        val localDate = OffsetDateTime.ofInstant(
            LocalDateTime.of(2020, 7, 20, 8, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        )

        log.info(message, nasdaqClose, localDate)
        // When requesting on a Monday in SG, you won't have COB prices until Tuesday in SG
        assertThat(previousClose.getPriceDate(localDate, nasdaq, true))
            .isEqualTo(nasdaqClose)
    }

    @Test
    fun is_AfternoonDateResolvedForCurrent() {
        // val utcDateUtils = DateUtils("UTC")
        val requestedDate = OffsetDateTime.ofInstant(
            LocalDateTime.of(2021, 9, 24, 12, 5).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        )
        assertThat(previousClose.getPriceDate(requestedDate, nasdaq, true))
            .isEqualTo(dateUtils.getDate("2021-09-23")) // Nasdaq last close
    }

    @Test
    fun is_MorningDateResolvedForCurrent() {
        val onTheMorningOf = OffsetDateTime.ofInstant(
            LocalDateTime.of(2021, 9, 29, 6, 15).toInstant(ZoneOffset.UTC),
            dateUtils.getZoneId()
        )
        // Should resolve to previous days close
        assertThat(previousClose.getPriceDate(onTheMorningOf, nasdaq, true))
            .isEqualTo(dateUtils.getDate("2021-09-28")) // Nasdaq last close
    }

    @Test
    fun is_PreviousDayUTCAfterMarketClose() {
        val utcEve = OffsetDateTime.ofInstant(
            LocalDateTime.of(2021, 10, 12, 22, 15).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        )
        // Should resolve to previous days close on the same day.
        assertThat(previousClose.getPriceDate(utcEve, nasdaq, true))
            .isEqualTo(dateUtils.getDate("2021-10-12"))

        val ofAsset = PriceRequest.of(AssetUtils.getAsset(Market("Blah", USD), "Test"))
        assertThat(ofAsset.date)
            .isEqualTo(DateUtils.today)
    }

    @Test
    fun is_AfternoonDateResolvedForHistoric() {
        val asAtDate = OffsetDateTime.ofInstant(
            LocalDateTime.of(2021, 9, 24, 15, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        )

        assertThat(previousClose.getPriceDate(asAtDate, nasdaq, false))
            .isEqualTo(dateUtils.getDate("2021-09-24")) // Nasdaq last close
    }

    @Test
    fun is_MarketDateCalculatedWhenToday() {
        val pricesUnavailable = OffsetDateTime.ofInstant(
            LocalDateTime.of(2020, 7, 17, 14, 0).toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC
        )

        // Requesting today, but prices are not yet available.
        val todayNoPrices = previousClose.getPriceDate(pricesUnavailable, nasdaq, true)
        assertThat(todayNoPrices).isEqualTo(dateUtils.getDate("2020-07-16"))

        val pricesAvailable = OffsetDateTime.ofInstant(
            LocalDateTime.of(2020, 7, 18, 6, 0).toInstant(ZoneOffset.UTC), // Avail next day
            dateUtils.getZoneId()
        )

        val todayHasPrices = previousClose.getPriceDate(pricesAvailable, nasdaq, true)
        assertThat(todayHasPrices).isEqualTo(dateUtils.getDate("2020-07-17"))
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
