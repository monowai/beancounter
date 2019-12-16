package com.beancounter.marketdata.currency;

import com.beancounter.common.model.Currency;
import com.beancounter.marketdata.config.StaticConfig;
import java.util.Collection;
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
  private CurrencyRepository currencyRepository;

  @Autowired
  void setMarkets(StaticConfig staticConfig) {
    this.staticConfig = staticConfig;
  }

  @Autowired(required = false)
  void setCurrencyRepository(CurrencyRepository currencyRepository) {
    this.currencyRepository = currencyRepository;
  }

  public void loadDefaultCurrencies(Collection<Currency>currencies) {
    if (currencyRepository != null) {
      currencyRepository.saveAll(currencies);
    }
  }

  /**
   * Resolves a currency via its ISO code (AK).
   *
   * @param code non-null code
   * @return resolved currency
   */
  public Currency getCode(@NotNull String code) {
    Objects.requireNonNull(code);
    return staticConfig.getCurrencyByCode().get(code.toUpperCase());
  }

  public Currency getBase() {
    return staticConfig.getBase();
  }

  public String delimited(String delimiter) {
    return String.join(delimiter, staticConfig.getCurrencyByCode().keySet());
  }
}
