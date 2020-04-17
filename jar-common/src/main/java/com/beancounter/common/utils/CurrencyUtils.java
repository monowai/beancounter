package com.beancounter.common.utils;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.IsoCurrencyPair;
import java.math.BigDecimal;
import lombok.experimental.UtilityClass;

@UtilityClass
public class CurrencyUtils {
  public Currency getCurrency(String isoCode) {
    if ( isoCode == null) {
      return null;
    }
    return Currency.builder().code(isoCode).build();
  }

  public IsoCurrencyPair getCurrencyPair(BigDecimal rate, Currency from, Currency to) {
    IsoCurrencyPair isoCurrencyPair = null;
    if (from == null || to == null) {
      return null;
    }

    if (rate == null && !from.getCode().equalsIgnoreCase(to.getCode())) {
      isoCurrencyPair = IsoCurrencyPair.builder()
          .from(from.getCode())
          .to(to.getCode())
          .build();
    }
    return isoCurrencyPair;
  }
}
