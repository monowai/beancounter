package com.beancounter.position.portfolio;

import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import com.beancounter.position.integration.BcGateway;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PortfolioService {
  private Map<String, Currency> currencies;

  @Autowired
  void setBcGateway(BcGateway bcGateway) {

    this.currencies = bcGateway.getCurrencies().getData();
  }

  public Collection<Portfolio> getPortfolios() {
    Collection<Portfolio> portfolios = new ArrayList<>();
    Portfolio sgd = Portfolio.builder()
        .code("test")
        .name("NZD Portfolio")
        .currency(currencies.get("NZD"))
        .base(currencies.get("USD"))
        .build();

    Portfolio gbp = Portfolio.builder()
        .code("MikeGBP")
        .name("GBP Portfolio")
        .currency(currencies.get("GBP"))
        .base(currencies.get("USD"))
        .build();
    portfolios.add(sgd);
    portfolios.add(gbp);

    return portfolios;

  }
}
