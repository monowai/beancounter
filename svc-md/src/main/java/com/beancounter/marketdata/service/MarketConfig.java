package com.beancounter.marketdata.service;

import com.beancounter.common.model.Market;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Access to the configured market objects managed by this service.
 *
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

  /**
   * Used to set the Map of Markets into the configuration.
   *
   * @param markets Keyed by Code, each Market will be built from the properties
   */
  public void setMarkets(Map<String, Map<String, Object>> markets) {
    // ToDo: This looks cumbersome. Must be a better way to deserialize
    for (String code : markets.keySet()) {
      Map<String, Object> marketValues = markets.get(code);
      Map<String, String> aliases = null;
      if (marketValues.containsKey("aliases")) {
        aliases = (Map<String, String>) marketValues.get("aliases");
      }

      Market market = Market
          .builder()
          .code(code)
          .aliases(aliases)
          .timezone(getTimeZone(marketValues)
          ).build();
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

}
