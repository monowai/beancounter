package com.beancounter.common

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MarketUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

internal class MarketUtilsTest {
    // System default timezone
    private var dateUtils = DateUtils("Asia/Singapore")
    private var marketUtils = MarketUtils(dateUtils)
    private val nasdaq = Market("NASDAQ", Currency("USD"), "US/Eastern")

    companion object {
        private val log = org.slf4j.LoggerFactory.getLogger(MarketUtilsTest::class.java)
    }

    @Test
    fun is_MarketDateCalculated() {
        val nasdaqClose = dateUtils.getDate("2020-07-17", dateUtils.getZoneId())
        var localDate = LocalDateTime.of(2020, 7, 20, 8, 0)
        log.info("{} {}", nasdaqClose, localDate)
        // When requesting on a Monday in SG, you won't have COB prices until Tuesday in SG
        assertThat(marketUtils.getPreviousClose(localDate, nasdaq, true))
            .isEqualTo(nasdaqClose)

        // Monday, 8pm SGT, prices are still not available, so should return Friday.
        localDate = LocalDateTime.of(2020, 7, 20, 20, 0)
        log.info("{} {}", nasdaqClose, localDate)
        assertThat(marketUtils.getPreviousClose(localDate, nasdaq, true))
            .isEqualTo(nasdaqClose)
    }

    @Test
    fun is_TimeIgnored() {
        val asAtDate = LocalDateTime.of(2021, 3, 4, 6, 30)
        val expected = dateUtils.getDate("2021-03-03")
        log.info("{} {}", asAtDate, expected)
        assertThat(marketUtils.getPreviousClose(asAtDate, nasdaq, true))
            .isEqualTo(expected) // Nasdaq last close
    }

    @Test
    fun is_MarketDateCalculatedWhenToday() {
        val pricesUnavailable = LocalDateTime.of(2020, 7, 17, 14, 0)
        val pricesAvailable = LocalDateTime.of(2020, 7, 18, 6, 0) // Avail next day
        // Requesting today, but prices are not yet available.
        val todayNoPrices = marketUtils.getPreviousClose(pricesUnavailable, nasdaq, true)
        assertThat(todayNoPrices).isEqualTo(dateUtils.getDate("2020-07-16"))
        val todayHasPrices = marketUtils.getPreviousClose(pricesAvailable, nasdaq, true)
        assertThat(todayHasPrices).isEqualTo(dateUtils.getDate("2020-07-17"))
    }

    @Test
    fun is_FridayFoundFromSundayInSystemDefaultTz() {
        val sgx = Market("SGX", Currency("SGD"), "Asia/Singapore")
        val sunday = sunday
        val found = marketUtils.getPreviousClose(LocalDateTime.of(sunday, LocalTime.MIDNIGHT), sgx)
        assertThat(dateUtils.convert(found)).isEqualTo(friday)
    }

    @Test
    fun is_WeekendFound() {
        assertThat(marketUtils.isMarketOpen(sunday)).isFalse // Sunday
        assertThat(marketUtils.isMarketOpen(saturday)).isFalse // Saturday
        assertThat(marketUtils.isMarketOpen(friday)).isTrue // Friday
        assertThat(marketUtils.isMarketOpen(monday)).isTrue // Monday
    }

    private val friday: LocalDate get() = dateUtils.getDate("2019-10-18")
    private val sunday: LocalDate get() = dateUtils.getDate("2019-10-20")
    private val saturday: LocalDate get() = dateUtils.getDate("2019-10-19")
    private val monday: LocalDate get() = dateUtils.getDate("2019-10-21")
}
