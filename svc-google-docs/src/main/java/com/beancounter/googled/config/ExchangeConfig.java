package com.beancounter.googled.config;

import java.util.Map;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Allow remapping of exchange related code data.
 *
 * @author mikeh
 * @since 2019-02-13
 */
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "com.beancounter.exchanges")
@Component
public class ExchangeConfig {

  @Getter
  @Setter
  private Map<String, String> aliases;

  /**
   * Return the Exchange code to use for the supplied input.
   * 
   * @param input code that *might* have an alias.
   * @return the alias or input if no exception is defined.
   */
  public String resolveAlias(String input) {
    String alias = aliases.get(input);
    if (alias == null) {
      return input;
    } else {
      return alias;
    }

  }
}
