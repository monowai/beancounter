package com.beancounter.ingest;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;

public class UnitTestHelper {

  public static Portfolio getPortfolio() {
    return getPortfolio(getCurrency("NZD"));
  }

  public static Portfolio getPortfolio(Currency baseCurrency) {
    return Portfolio.builder()
        .code("TEST")
        .currency(baseCurrency)
        .build();
  }

  public static Currency getCurrency(String currency) {
    return Currency.builder().code(currency).build();
  }


}
