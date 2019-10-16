package com.beancounter.common.utils;

import com.beancounter.common.model.Currency;

public class CurrencyUtils {
  public static Currency getCurrency(String isoCode) {
    return Currency.builder().code(isoCode).build();
  }
}
