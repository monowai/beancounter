package com.beancounter.marketdata.service;

import com.beancounter.common.model.Market;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

/**
 * @author mikeh
 * @since 2019-03-19
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter")
@Component
@Slf4j
@Data
public class MarketConfig {
  private Map<String, Market> marketMap = new HashMap<>();

  public void setMarkets(Map<String, Map<String, Object>> markets) {
    for (String code : markets.keySet()) {
      Map<String, Object> marketValues = markets.get(code);
      Market market = Market
              .builder()
              .code(code)
          .timezone(getTimeZone(marketValues)).build();
      marketMap.put(code, market);
    }
    log.info(markets.toString());
  }

  private TimeZone getTimeZone(Map<String, Object> market) {
    Object result = market.get("timezone");
    if (result == null) {
      return TimeZone.getDefault();
    }
    return TimeZone.getTimeZone(result.toString());
  }

  public boolean isWorkDay() {
    return true;
  }
}
