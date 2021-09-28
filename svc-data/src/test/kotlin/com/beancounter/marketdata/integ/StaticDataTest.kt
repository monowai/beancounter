package com.beancounter.marketdata.integ

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.PreviousClosePriceDate
import com.beancounter.marketdata.Constants.Companion.ASX
import com.beancounter.marketdata.Constants.Companion.NASDAQ
import com.beancounter.marketdata.Constants.Companion.NYSE
import com.beancounter.marketdata.Constants.Companion.NZD
import com.beancounter.marketdata.Constants.Companion.NZX
import com.beancounter.marketdata.Constants.Companion.USD
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.mock.MockProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest
@EntityScan("com.beancounter.common.model")
@EnableAutoConfiguration
@ActiveProfiles("test")
/**
 * Integration tests for static data related functionality.
 */
internal class StaticDataTest @Autowired constructor(
    private val marketService: MarketService,
    private val currencyService: CurrencyService,
    dateUtils: DateUtils
) {

    private val marketUtils = PreviousClosePriceDate(dateUtils)

    @Test
    fun is_FoundForAlias() {
        val nyse = marketService.getMarket(NYSE.code)
        val nzx = marketService.getMarket(NZX.code)
        val asx = marketService.getMarket(ASX.code)
        val nasdaq = marketService.getMarket(NASDAQ.code)
        AssertionsForClassTypes.assertThat(marketService.getMarket("nys")).isEqualTo(nyse)
        AssertionsForClassTypes.assertThat(marketService.getMarket("NZ")).isEqualTo(nzx)
        AssertionsForClassTypes.assertThat(marketService.getMarket("AX")).isEqualTo(asx)
        AssertionsForClassTypes.assertThat(marketService.getMarket("NAS")).isEqualTo(nasdaq)
    }

    @Test
    fun does_MockMarketConfigurationExist() {
        val market = marketService.getMarket(MockProviderService.ID)
        AssertionsForClassTypes.assertThat(market)
            .isNotNull
            .hasFieldOrPropertyWithValue("timezone", TimeZone.getTimeZone(ZoneOffset.UTC))
            .hasFieldOrProperty("currency")
        AssertionsForClassTypes.assertThat(market.currency)
            .hasFieldOrPropertyWithValue("code", USD.code)
    }

    @Test
    fun is_IgnoreAliasLookup() {
        // Alias exists, but no PK with this code
        Assertions.assertThrows(BusinessException::class.java) { marketService.getMarket("US", false) }
    }

    @Test
    fun is_AliasForWtdAndNzxResolving() {
        val market = marketService.getMarket(NZX.code)
        AssertionsForClassTypes.assertThat(market)
            .isNotNull
            .hasFieldOrProperty("aliases")
        AssertionsForClassTypes.assertThat(market.currency)
            .hasFieldOrPropertyWithValue("code", NZD.code)
        AssertionsForClassTypes.assertThat(market.aliases[WtdService.ID])
            .isEqualTo("NZ")
            .isNotNull
    }

    @Test
    fun does_MarketDataAliasNasdaqResolveToNull() {
        val market = marketService.getMarket(NASDAQ.code)
        AssertionsForClassTypes.assertThat(market)
            .isNotNull
            .hasFieldOrProperty("aliases")
        AssertionsForClassTypes.assertThat(market.aliases[WtdService.ID])
            .isBlank
    }

    @Test
    fun is_CurrencyDataLoading() {
        AssertionsForClassTypes.assertThat(currencyService.getCode(USD.code))
            .isNotNull
        AssertionsForClassTypes.assertThat(currencyService.baseCurrency)
            .isNotNull
    }

    @Test
    fun is_IllegalArgumentsHandled() {
        Assertions.assertThrows(BusinessException::class.java) { marketService.getMarket(null, true) }
    }
}
