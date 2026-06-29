package com.beancounter.marketdata.providers.marketstack

import com.beancounter.common.model.Asset
import com.beancounter.common.model.Market
import com.beancounter.common.utils.BcJson.Companion.objectMapper
import com.beancounter.common.utils.DateUtils
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.marketstack.MarketStackConfig.Companion.DATE_TIME_FORMAT
import com.beancounter.marketdata.providers.marketstack.model.MarketStackResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.kotlin.whenever
import org.springframework.core.io.ClassPathResource
import tools.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * MarketStack api response tests.
 */
class MarketStackResponseTest {
    private val dateUtils = DateUtils()

    /**
     * Test Constants for MarketStack
     */
    companion object {
        const val CONTRACTS = "/mock/mstack"
    }

    @Test
    fun jsonGoodResponse() {
        val compareTo =
            ZonedDateTime.of(
                LocalDateTime.parse(
                    "2024-11-29T00:00:00+0000",
                    DateTimeFormatter.ofPattern(DATE_TIME_FORMAT)
                ),
                ZoneId.of("UTC")
            )

        val jsonFile = ClassPathResource("$CONTRACTS/AAPL-MSFT.json").file
        val response = objectMapper.readValue<MarketStackResponse>(jsonFile)

        assertThat(response.data).isNotEmpty.hasSize(2)

        response.data.forEach {
            assertThat(it).hasFieldOrPropertyWithValue(
                "date",
                compareTo.toLocalDateTime()
            )
        }
    }

    @Test
    fun jsonResponseWithMessage() {
        val jsonFile = ClassPathResource("$CONTRACTS/no-data.json").file
        val response = objectMapper.readValue<MarketStackResponse>(jsonFile)
        assertThat(response).isNotNull.hasFieldOrProperty("error.message")
    }

    @Test
    fun dateAssumptionsValid() {
        val marketStackConfig = MarketStackConfig(Mockito.mock(MarketService::class.java))
        assertThat(marketStackConfig.date).isEqualTo(dateUtils.today())

        val date = "2020-02-06"
        marketStackConfig.date = date
        assertThat(marketStackConfig.date).isEqualTo(date)
        marketStackConfig.date = dateUtils.today()
        assertThat(marketStackConfig.date).isEqualTo(dateUtils.today())
    }

    @Test
    fun `getPriceCode ignores bare priceSymbol and uses market alias`() {
        val marketService = Mockito.mock(MarketService::class.java)
        val sgxMarket = Market(code = "SGX", currencyId = "SGD", aliases = mapOf("mstack" to "SI"))
        whenever(marketService.getMarket("SGX")).thenReturn(sgxMarket)

        val config = MarketStackConfig(marketService)
        // priceSymbol == code (no exchange suffix) — MarketStack must not get bare "SCI"
        // which resolves to NYSE:SCI (Service Corp International) instead of SGX:SCI
        val asset = Asset(code = "SCI", market = sgxMarket, priceSymbol = "SCI")
        assertThat(config.getPriceCode(asset)).isEqualTo("SCI.SI")
    }

    @Test
    fun `getPriceCode honours priceSymbol when it carries an exchange suffix`() {
        val marketService = Mockito.mock(MarketService::class.java)
        val sgxMarket = Market(code = "SGX", currencyId = "SGD", aliases = mapOf("mstack" to "SI"))
        whenever(marketService.getMarket("SGX")).thenReturn(sgxMarket)

        val config = MarketStackConfig(marketService)
        val asset = Asset(code = "D05", market = sgxMarket, priceSymbol = "D05.SI")
        assertThat(config.getPriceCode(asset)).isEqualTo("D05.SI")
    }
}