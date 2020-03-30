package com.beancounter.common.utils;

import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import lombok.experimental.UtilityClass;

@UtilityClass
public class PortfolioUtils {

  public PortfolioInput getPortfolioInput(String code) {
    return PortfolioInput.builder().code(code).currency("NZD").build();
  }

  public Portfolio getPortfolio(String code) {
    return getPortfolio(code, Currency.builder().code("NZD").build());
  }

  public Portfolio getPortfolio(String code, Currency currency) {
    return Portfolio.builder()
        .id(code)
        .code(code)
        .currency(currency)
        .build();
  }
}
