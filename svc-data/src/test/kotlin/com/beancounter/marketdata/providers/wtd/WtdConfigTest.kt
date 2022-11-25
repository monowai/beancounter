package com.beancounter.marketdata.providers.wtd

import com.beancounter.common.contracts.Payload
import com.beancounter.common.utils.BcJson
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.markets.MarketService
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.core.io.ClassPathResource
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * WorldTradingData api configuration.
 */
class WtdConfigTest {
    private val dateUtils = DateUtils()
    private val objectMapper: ObjectMapper = BcJson().objectMapper

    companion object {
        const val CONTRACTS = "/mock/wtd"
    }

    @Test
    @Throws(Exception::class)
    fun jsonGoodResponse() {
        val jsonFile = ClassPathResource("$CONTRACTS/AAPL-MSFT.json").file
        val response = this.objectMapper.readValue(jsonFile, WtdResponse::class.java)
        val compareTo = ZonedDateTime.of(
            LocalDate.parse("2019-03-08").atStartOfDay(),
            ZoneId.of("UTC")
        )
        assertThat(response)
            .isNotNull
            .hasFieldOrPropertyWithValue(
                "date",
                compareTo.toLocalDate().toString()
            )
            .hasFieldOrProperty(Payload.DATA)
    }

    @Test
    @Throws(Exception::class)
    fun jsonResponseWithMessage() {
        val jsonFile = ClassPathResource("$CONTRACTS/NoData.json").file
        val response = this.objectMapper.readValue(jsonFile, WtdResponse::class.java)
        assertThat(response)
            .isNotNull
            .hasFieldOrProperty("message")
    }

    @Test
    fun dateAssumptionsValid() {
        val wtdConfig = WtdConfig(Mockito.mock(MarketService::class.java))
        assertThat(wtdConfig.date).isEqualTo(dateUtils.today())

        val date = "2020-02-06"
        wtdConfig.date = date
        assertThat(wtdConfig.date).isEqualTo(date)
        wtdConfig.date = dateUtils.today()
        assertThat(wtdConfig.date).isEqualTo(dateUtils.today())
    }
}
