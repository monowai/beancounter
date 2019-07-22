package com.beancounter.marketdata.service;

import com.beancounter.common.exception.BusinessException;
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
  private void setMarkets(StaticConfig staticConfig) {
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
    Currency currency = staticConfig.getCurrencyId().get(code.toUpperCase());
    if (currency == null) {
      throw new BusinessException(String.format("Currency not found %s", code));
    }
    return currency;
  }

  /**
   * Resolves a currency via its ISO code (AK).
   *
   * @param code non-null code
   * @return resolved currency
   */
  public Currency getCode(@NotNull String code) {
    Objects.requireNonNull(code);
    Currency currency = staticConfig.getCurrencyCode().get(code.toUpperCase());
    if (currency == null) {
      throw new BusinessException(String.format("Currency not found %s", code));
    }
    return currency;
  }

  public Currency getBase() {
    return staticConfig.getBase();
  }


}
