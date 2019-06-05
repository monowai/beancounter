package com.beancounter.marketdata.service;

import com.beancounter.common.model.Market;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

@SuppressWarnings("ALL")
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "beancounter")
@Component
@Slf4j
@Data
public class CurrencyConfig {
  private Map<String, Market> currencyMap = new HashMap<>();

  /**
   * Used to set the Map of Markets into the configuration.
   *
   * @param marketMap Keyed by Code, each Market will be built from the properties
   */
  public void setCurrencies(Map<String, Map<String, Object>> marketMap) {
    log.info("" + marketMap.size());
  }
}
