package com.beancounter.marketdata;

import static java.time.ZoneOffset.UTC;
import static org.assertj.core.api.Java6Assertions.assertThat;

import com.beancounter.common.model.Market;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.providers.wtd.WtdProviderService;
import com.beancounter.marketdata.service.CurrencyConfig;
import com.beancounter.marketdata.service.MarketConfig;
import com.beancounter.marketdata.service.MarketService;
import com.beancounter.marketdata.util.Dates;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
@SpringBootTest(classes = {MarketService.class, CurrencyConfig.class, MarketConfig.class})
class TestMarkets {

  private MarketConfig marketConfig;

  private MarketService marketService;

  @Autowired
  TestMarkets(MarketConfig marketConfig,
              MarketService marketService) {
    this.marketService = marketService;
    this.marketConfig = marketConfig;
  }

  @Test
  void marketConfiguration_MockExists() {

    assertThat(marketConfig).isNotNull();
    Market market = marketService.getMarket(MockProviderService.ID);
    assertThat(market)
        .isNotNull()
        .hasFieldOrPropertyWithValue("timezone", TimeZone.getTimeZone(UTC))
        .hasFieldOrPropertyWithValue("currency", "US")
    ;

  }

  @Test
  void computeMarketDataProvider_PriceDateFromUserTz() {

    //  The java.util.Date has no concept of time zone, and only represents
    //  the number of seconds passed since the Unix epoch time – 1970-01-01T00:00:00Z.
    //  But, if you print the Date object directly, it is always printed with the default
    //  system time zone.

    String dateFormat = "yyyy-M-dd hh:mm:ss a";
    String dateInString = "2019-4-14 10:30:00 AM";
    // Users requested date "today in timezone"

    LocalDateTime sunday = LocalDateTime
        .parse(dateInString, DateTimeFormatter.ofPattern(dateFormat));

    Dates dates = new Dates();


    Market sgMarket = marketService.getMarket("SGX");
    Market nzMarket = marketService.getMarket("NZX");
    LocalDate resolvedDate = dates.getLastMarketDate(
        sunday
            .atZone(sgMarket.getTimezone().toZoneId()),
        marketService.getMarket("NYSE").getTimezone().toZoneId());

    assertThat(resolvedDate)
        .hasFieldOrPropertyWithValue("dayOfWeek", DayOfWeek.FRIDAY)
        .hasFieldOrPropertyWithValue("dayOfMonth", 12)
    ;

    resolvedDate = dates.getLastMarketDate(sunday
            .atZone(nzMarket.getTimezone().toZoneId()),
        marketService.getMarket("NYSE").getTimezone().toZoneId());

    assertThat(resolvedDate)
        .hasFieldOrPropertyWithValue("dayOfWeek", DayOfWeek.FRIDAY)
        .hasFieldOrPropertyWithValue("dayOfMonth", 12)
    ;

  }

  @Test
  void marketDataAlias_WtdAndNzx() {
    Market market = marketService.getMarket("NZX");
    assertThat(market)
        .isNotNull()
        .hasFieldOrProperty("aliases");

    assertThat(market.getAliases()
        .get(WtdProviderService.ID))
        .isEqualTo("NZ")
        .isNotNull();
  }

  @Test
  void marketDataAlias_NasdaqResolvesToNull() {
    Market market = marketService.getMarket("NASDAQ");
    assertThat(market)
        .isNotNull()
        .hasFieldOrProperty("aliases");

    assertThat(market.getAliases()
        .get(WtdProviderService.ID))
        .isBlank();
  }


}
