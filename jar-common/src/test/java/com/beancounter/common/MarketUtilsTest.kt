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
    private var dateUtils = DateUtils()
    private var marketUtils = MarketUtils()
    private val nasdaq = Market("NASDAQ", Currency("USD"), "US/Eastern")

    @Test
    fun is_MarketDateCalculated() {
        val lastPriceDate = dateUtils.getDate("2020-07-17", dateUtils.getZoneId())!! // Expect Fridays Price

        // When requesting on a Monday in SG, you won't have COB prices until Tuesday in SG
        assertThat(marketUtils.getLastMarketDate(LocalDateTime.of(2020, 7, 20, 8, 0), nasdaq, true))
                .isEqualTo(lastPriceDate)

        // Monday, 8pm SGT, prices are still not available, so should return Friday.
        assertThat(marketUtils.getLastMarketDate(LocalDateTime.of(2020, 7, 20, 20, 0), nasdaq, true))
                .isEqualTo(lastPriceDate)


    }

    @Test
    fun is_MarketDateCalculatedWhenToday() {
        val pricesUnavailable = LocalDateTime.of(2020, 7, 17, 14, 0)
        val pricesAvailable = LocalDateTime.of(2020, 7, 18, 6, 0)// Avail next day
        // Requesting today, but prices are not yet available.
        val todayNoPrices = marketUtils.getLastMarketDate(pricesUnavailable, nasdaq, true)
        assertThat(todayNoPrices).isEqualTo(dateUtils.getDate("2020-07-16"))
        val todayHasPrices = marketUtils.getLastMarketDate(pricesAvailable, nasdaq, true)
        assertThat(todayHasPrices).isEqualTo(dateUtils.getDate("2020-07-17"))

    }

    @Test
    fun is_FridayFoundFromSundayInSystemDefaultTz() {
        val sgx = Market("SGX", Currency("SGD"), "Asia/Singapore")
        val sunday = sunday
        val found = marketUtils.getLastMarketDate(LocalDateTime.of(sunday, LocalTime.MIDNIGHT), sgx)
        assertThat(dateUtils.convert(found)).isEqualTo(friday)
    }

    @Test
    fun is_WeekendFound() {
        assertThat(marketUtils.isMarketOpen(sunday)).isFalse() // Sunday
        assertThat(marketUtils.isMarketOpen(saturday)).isFalse() // Saturday
        assertThat(marketUtils.isMarketOpen(friday)).isTrue() // Friday
        assertThat(marketUtils.isMarketOpen(monday)).isTrue() // Monday
    }


    private val friday: LocalDate get() = dateUtils.getDate("2019-10-18")!!
    private val sunday: LocalDate get() = dateUtils.getDate("2019-10-20")!!
    private val saturday: LocalDate get() = dateUtils.getDate("2019-10-19")!!
    private val monday: LocalDate get() = dateUtils.getDate("2019-10-21")!!


}
