package com.beancounter.marketdata.service;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import javax.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Static data configuration
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
  private Map<String, Map<String, Object>> currencies;
  private Map<String, Map<String, Object>> markets;

  private Map<String, Market> marketData = new HashMap<>();

  private Map<String, Currency> currencyId = new HashMap<>();
  private Map<String, Currency> currencyCode = new HashMap<>();

  private Currency base;

  /**
   * Convert from configured representation to Objects
   */
  @PostConstruct
  void configure() {
    handleCurrencies();
    handleMarkets();

  }

  private void handleMarkets() {
    // ToDo: This looks cumbersome. Must be a better way to deserialize
    for (String code : markets.keySet()) {
      Map<String, Object> marketValues = markets.get(code);
      Map<String, String> aliases = null;
      if (marketValues.containsKey("aliases")) {
        aliases = (Map<String, String>) marketValues.get("aliases");
      }

      Object currencyId = markets.get(code).get("currencyId");
      Currency currency = (currencyId == null ?
          getBase() :
          this.currencyId.get(currencyId.toString()));


      Market market = Market
          .builder()
          .code(code)
          .aliases(aliases)
          .currency(currency)
          .timezone(getTimeZone(marketValues)
          ).build();
      this.marketData.put(code, market);
    }
    log.info(markets.toString());
  }

  private void handleCurrencies() {
    for (String code : currencies.keySet()) {
      Currency currency = Currency.of(code, currencies.get(code));
      currencyId.put(code, currency);
      currencyCode.put(currency.getCode(), currency);
    }
    base = currencyId.get("US");
  }

  public Currency getBase() {
    return base;
  }


  private TimeZone getTimeZone(Map<String, Object> market) {
    Object result = market.get("timezone");
    if (result == null) {
      return TimeZone.getDefault();
    }
    return TimeZone.getTimeZone(result.toString());
  }

}
