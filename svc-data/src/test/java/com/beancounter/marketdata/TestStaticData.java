package com.beancounter.marketdata;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import com.beancounter.common.model.Market;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.config.StaticConfig;
import com.beancounter.marketdata.currency.CurrencyService;
import com.beancounter.marketdata.markets.MarketService;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.providers.wtd.WtdService;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Market related tests.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@SpringBootTest(classes = {
    CurrencyService.class,
    MarketService.class,
    StaticConfig.class})
class TestStaticData {

  private StaticConfig staticConfig;
  private MarketService marketService;
  private CurrencyService currencyService;

  @Autowired
  TestStaticData(StaticConfig staticConfig,
                 MarketService marketService,
                 CurrencyService currencyService
  ) {
    this.marketService = marketService;
    this.staticConfig = staticConfig;
    this.currencyService = currencyService;
  }

  @Test
  void does_MockMarketConfigurationExist() {

    assertThat(staticConfig).isNotNull();
    Market market = marketService.getMarket(MockProviderService.ID);
    assertThat(market)
        .isNotNull()
        .hasFieldOrPropertyWithValue("timezone", TimeZone.getTimeZone(UTC))
        .hasFieldOrProperty("currency")
    ;

    assertThat(market.getCurrency())
        .hasFieldOrPropertyWithValue("code", "USD");

  }

  @Test
  void is_serTzComputed() {

    //  The java.util.Date has no concept of time zone, and only represents
    //  the number of seconds passed since the Unix epoch time – 1970-01-01T00:00:00Z.
    //  But, if you print the Date object directly, it is always printed with the default
    //  system time zone.

    String dateFormat = "yyyy-M-dd hh:mm:ss a";
    String dateInString = "2019-4-14 10:30:00 AM";
    // Users requested date "today in timezone"

    LocalDate sunday = LocalDate
        .parse(dateInString, DateTimeFormatter.ofPattern(dateFormat));

    LocalDate resolvedDate = DateUtils.getLastMarketDate(
        sunday,
        marketService.getMarket("NYSE").getTimezone().toZoneId());

    assertThat(resolvedDate)
        .isEqualTo(LocalDate.of(2019, 4, 12))
    ;

    resolvedDate = DateUtils.getLastMarketDate(sunday,
        marketService.getMarket("NYSE").getTimezone().toZoneId());

    assertThat(resolvedDate)
        .isEqualTo(LocalDate.of(2019, 4, 12))
    ;

  }

  @Test
  void is_MarketDataAliasForWtdAndNzxResolving() {
    Market market = marketService.getMarket("NZX");
    assertThat(market)
        .isNotNull()
        .hasFieldOrProperty("aliases");

    assertThat(market.getCurrency())
        .hasFieldOrPropertyWithValue("code", "NZD");

    assertThat(market.getAliases()
        .get(WtdService.ID))
        .isEqualTo("NZ")
        .isNotNull();
  }

  @Test
  void does_MarketDataAliasNasdaqResolveToNull() {
    Market market = marketService.getMarket("NASDAQ");
    assertThat(market)
        .isNotNull()
        .hasFieldOrProperty("aliases");

    assertThat(market.getAliases()
        .get(WtdService.ID))
        .isBlank();
  }

  @Test
  void is_CurrencyDataLoading() {

    assertThat(currencyService.getCode("USD"))
        .isNotNull();

    assertThat(currencyService.getBase())
        .isNotNull();
  }
}
