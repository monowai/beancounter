package com.beancounter.marketdata

import com.beancounter.common.model.Currency
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.providers.wtd.WtdConfig
import com.beancounter.marketdata.providers.wtd.WtdResponse
import com.beancounter.marketdata.utils.WtdMockUtils
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.*

class WtdConfigTest {
    private val dateUtils = DateUtils()

    private val mapper = objectMapper

    @Test
    @Throws(Exception::class)
    fun is_JsonGoodResponse() {
        val jsonFile = ClassPathResource(WtdMockUtils.WTD_PATH + "/AAPL-MSFT.json").file
        val response = mapper.readValue(jsonFile, WtdResponse::class.java)
        val compareTo = ZonedDateTime.of(
                LocalDate.parse("2019-03-08").atStartOfDay(), ZoneId.of("UTC")
        )
        assertThat(response)
                .isNotNull
                .hasFieldOrPropertyWithValue("date", DateUtils()
                        .getDateString(compareTo.toLocalDate()))
                .hasFieldOrProperty("data")
    }

    @Test
    @Throws(Exception::class)
    fun is_JsonResponseWithMessage() {
        val jsonFile = ClassPathResource(WtdMockUtils.WTD_PATH + "/NoData.json").file
        val response = mapper.readValue(jsonFile, WtdResponse::class.java)
        assertThat(response)
                .isNotNull
                .hasFieldOrProperty("message")
    }

    @Test
    fun is_NzxValuationDateCorrect() {
        val wtdConfig = WtdConfig()
        val nzx = Market("NZX", Currency("NZD"))
        // Overriding today, so should just return today
        assertThat(wtdConfig.getMarketDate(nzx, "2019-11-15"))
                .isEqualTo("2019-11-15")

        // If it's Saturday, count back to Friday
        assertThat(wtdConfig.getMarketDate(nzx, "2019-11-17"))
                .isEqualTo("2019-11-15")
    }
    @Test
    fun is_DateAssumptionsValid() {
        val wtdConfig = WtdConfig()
        val today = dateUtils.today()
        assertThat(wtdConfig.date).isEqualTo(today)

        val date = "2020-02-06"
        wtdConfig.date = date
        assertThat(wtdConfig.date).isEqualTo(date)
        wtdConfig.date = today
        assertThat(wtdConfig.date).isEqualTo(today)

        // On AsAt the date is equal to the requested date
        val nzx = Market("NZX", Currency("NZD"))
        wtdConfig.date = date
        assertThat(wtdConfig.getMarketDate(nzx, date))
                .isEqualTo(date)

        // On Today, it should subtract 2 days
        val expectedDate = dateUtils.getLastMarketDate(
                dateUtils.getDate(today)!!, TimeZone.getTimeZone("US/Eastern").toZoneId(), 2)
        wtdConfig.date = today
        assertThat(wtdConfig.getMarketDate(nzx, today)).isEqualTo(expectedDate.toString())
    }
}