package com.beancounter.marketdata.integ

import com.beancounter.common.exception.BusinessException
import com.beancounter.common.model.Currency
import com.beancounter.common.utils.DateUtils
import com.beancounter.common.utils.MarketUtils
import com.beancounter.marketdata.config.MarketConfig
import com.beancounter.marketdata.currency.CurrencyRepository
import com.beancounter.marketdata.currency.CurrencyService
import com.beancounter.marketdata.markets.MarketService
import com.beancounter.marketdata.providers.mock.MockProviderService
import com.beancounter.marketdata.providers.wtd.WtdService
import org.assertj.core.api.AssertionsForClassTypes
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest(classes = [MarketService::class, CurrencyService::class, MarketConfig::class, DateUtils::class])
@EntityScan(basePackageClasses = [Currency::class])
@EnableAutoConfiguration
@EnableJpaRepositories(basePackageClasses = [CurrencyRepository::class])
internal class StaticDataTest @Autowired constructor(
    private val marketService: MarketService,
    private val currencyService: CurrencyService,
    dateUtils: DateUtils
) {

    private val marketUtils = MarketUtils(dateUtils)

    @Test
    fun is_FoundForAlias() {
        val nyse = marketService.getMarket("NYSE")
        val nzx = marketService.getMarket("NZX")
        val asx = marketService.getMarket("ASX")
        val nasdaq = marketService.getMarket("NASDAQ")
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
                .hasFieldOrPropertyWithValue("code", "USD")
    }

    @Test
    fun is_serTzComputed() {

        //  The java.util.Date has no concept of time zone, and only represents
        //  the number of seconds passed since the Unix epoch time – 1970-01-01T00:00:00Z.
        //  But, if you print the Date object directly, it is always printed with the default
        //  system time zone.
        val dateFormat = "yyyy-MM-dd hh:mm:ss"
        val dateInString = "2019-04-14 10:30:00"
        // Users requested date "today in timezone"
        val sunday = LocalDate
                .parse(dateInString, DateTimeFormatter.ofPattern(dateFormat))
        var resolvedDate = marketUtils.getLastMarketDate(
                sunday.atStartOfDay(),
                marketService.getMarket("NYSE"))
        AssertionsForClassTypes.assertThat(resolvedDate)
                .isEqualTo(LocalDate.of(2019, 4, 12))
        resolvedDate = marketUtils.getLastMarketDate(sunday.atStartOfDay(),
                marketService.getMarket("NYSE"))
        AssertionsForClassTypes.assertThat(resolvedDate)
                .isEqualTo(LocalDate.of(2019, 4, 12))
    }

    @Test
    fun is_IgnoreAliasLookup() {
        // Alias exists, but no PK with this code
        Assertions.assertThrows(BusinessException::class.java) { marketService.getMarket("US", false) }
    }

    @Test
    fun is_AliasForWtdAndNzxResolving() {
        val market = marketService.getMarket("NZX")
        AssertionsForClassTypes.assertThat(market)
                .isNotNull
                .hasFieldOrProperty("aliases")
        AssertionsForClassTypes.assertThat(market.currency)
                .hasFieldOrPropertyWithValue("code", "NZD")
        AssertionsForClassTypes.assertThat(market.aliases[WtdService.ID])
                .isEqualTo("NZ")
                .isNotNull()
    }

    @Test
    fun does_MarketDataAliasNasdaqResolveToNull() {
        val market = marketService.getMarket("NASDAQ")
        AssertionsForClassTypes.assertThat(market)
                .isNotNull
                .hasFieldOrProperty("aliases")
        AssertionsForClassTypes.assertThat(market.aliases[WtdService.ID])
                .isBlank()
    }

    @Test
    fun is_CurrencyDataLoading() {
        AssertionsForClassTypes.assertThat(currencyService.getCode("USD"))
                .isNotNull
        AssertionsForClassTypes.assertThat(currencyService.baseCurrency)
                .isNotNull
    }

    @Test
    fun is_IllegalArgumentsHandled() {
        Assertions.assertThrows(BusinessException::class.java) { marketService.getMarket(null, true) }
    }

}