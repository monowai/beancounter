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
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

/**
 * Static data configuration.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter")
@Component
@Slf4j
@Data
public class StaticConfig {
  private Collection<Currency> currencies;
  private Collection<Market> markets;

  private Map<String, Market> marketData = new HashMap<>();
  private Map<String, Currency> currencyByCode = new HashMap<>();

  private String baseCode; // System default
  private Currency baseCurrency;
  private CurrencyService currencyService;
  private MarketService marketService;

  /**
   * Convert from configured representation to Objects.
   */
  @PostConstruct
  void configure() {
    handleCurrencies();
    handleMarkets();

  }

  @Autowired(required = false)
  @DependsOn("currencyRepository")
  void setCurrencyService(CurrencyService currencyService) {
    this.currencyService = currencyService;
  }

  private void handleMarkets() {
    for (Market market : markets) {
      String currencyCode = market.getCurrencyId();
      Currency currency = (currencyCode == null
          ? getBase() :
          this.currencyByCode.get(currencyCode));
      market.setCurrency(currency);
      market.setTimezone(getTimeZone(market.getTimezoneId()));
      this.marketData.put(market.getCode(), market);
    }
  }

  private void handleCurrencies() {
    for (Currency currency : currencies) {
      currencyByCode.put(currency.getCode(), currency);
    }
    baseCurrency = currencyByCode.get(baseCode); // Default base currency
    if (currencyService != null) {
      currencyService.loadDefaultCurrencies(currencies);
    }
  }

  public Currency getBase() {
    return baseCurrency;
  }


  private TimeZone getTimeZone(String timezoneId) {
    if (timezoneId == null) {
      return TimeZone.getDefault();
    }
    return TimeZone.getTimeZone(timezoneId);
  }

}