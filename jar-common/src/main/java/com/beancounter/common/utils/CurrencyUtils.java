package com.beancounter.common.utils;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.CurrencyPair;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CurrencyUtils {
  public Currency getCurrency(String isoCode) {
    return Currency.builder().code(isoCode).build();
  }

  public CurrencyPair getCurrencyPair(BigDecimal rate, Currency from, Currency to) {
    CurrencyPair currencyPair = null;
    if (from == null || to == null) {
      return null;
    }

    if (rate == null && !from.getCode().equalsIgnoreCase(to.getCode())) {
      currencyPair = CurrencyPair.builder()
          .from(from.getCode())
          .to(to.getCode())
          .build();
    }
    return currencyPair;
  }
}
