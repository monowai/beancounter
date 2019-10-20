package com.beancounter.position;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

/**
 * General helper functions to support unit testing.
 *
 * @author mikeh
 * @since 2019-02-20
 */
public class TestUtils {

  private static ObjectMapper mapper = new ObjectMapper();

  @VisibleForTesting
  static Date convert(LocalDate localDate) {
    return Date.from(localDate.atStartOfDay()
        .atZone(ZoneId.systemDefault())
        .toInstant());
  }

  public static Portfolio getPortfolio(String code) {
    return getPortfolio(code, Currency.builder().code("NZD").build());
  }

  public static Portfolio getPortfolio(String code, Currency currency) {
    return Portfolio.builder()
        .code("TEST")
        .currency(currency)
        .build();
  }
}
