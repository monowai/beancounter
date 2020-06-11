package com.beancounter.marketdata.config;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.currency.CurrencyService;
import com.beancounter.marketdata.markets.MarketService;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Static data configuration.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter.market")
@Component
@Slf4j
@Data
public class MarketConfig {
  private Collection<Market> values;
  private Map<String, Market> providers = new HashMap<>();
  private CurrencyService currencyService;
  private MarketService marketService;

  /**
   * Convert from configured representation to Objects.
   */
  @PostConstruct
  void configure() {
    handleMarkets();
  }

  @Autowired
  void setCurrencyService(CurrencyService currencyService) {
    this.currencyService = currencyService;
  }

  private void handleMarkets() {
    for (Market market : values) {
      String currencyCode = market.getCurrencyId();
      Currency currency = (currencyCode == null
          ? currencyService.getBaseCurrency() :
          currencyService.getCode(currencyCode));
      market.setCurrency(currency);
      market.setTimezone(getTimeZone(market.getTimezoneId()));
      this.providers.put(market.getCode(), market);
    }
  }

  private TimeZone getTimeZone(String timezoneId) {
    if (timezoneId == null) {
      return TimeZone.getDefault();
    }
    return TimeZone.getTimeZone(timezoneId);
  }

}
