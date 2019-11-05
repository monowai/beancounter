package com.beancounter.marketdata.service;

import com.beancounter.common.model.Currency;
import com.beancounter.marketdata.config.StaticConfig;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Verification of Market related functions.
 *
 * @author mikeh
 * @since 2019-03-19
 */
@Service
@Slf4j
public class CurrencyService {

  private StaticConfig staticConfig;

  @Autowired
  void setMarkets(StaticConfig staticConfig) {
    this.staticConfig = staticConfig;
  }

  /**
   * Resolves a currency via its primary key.
   *
   * @param code non-null market
   * @return resolved currency
   */
  public Currency getId(@NotNull String code) {
    Objects.requireNonNull(code);
    return staticConfig.getCurrencyId().get(code.toUpperCase());
  }

  /**
   * Resolves a currency via its ISO code (AK).
   *
   * @param code non-null code
   * @return resolved currency
   */
  public Currency getCode(@NotNull String code) {
    Objects.requireNonNull(code);
    return staticConfig.getCurrencyCode().get(code.toUpperCase());
  }

  public Currency getBase() {
    return staticConfig.getBase();
  }


  public String delimited(String delimiter) {
    return String.join(delimiter, staticConfig.getCurrencyCode().keySet());
  }
}
