package com.beancounter.common.helper;

import com.beancounter.common.model.Currency;

public class CurrencyHelper {
  public static Currency getCurrency(String isoCode){
    return Currency.builder().code(isoCode).build();
  }
}
