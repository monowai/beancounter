package com.beancounter.marketdata.portfolio;

import com.beancounter.common.model.Portfolio;
import com.beancounter.marketdata.currency.CurrencyService;
import java.util.ArrayList;
import java.util.Collection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PortfolioService {
  private CurrencyService currencyService;
  private PortfolioRepository portfolioRepository;

  @Autowired
  void setCurrencyService(CurrencyService currencyService) {
    this.currencyService = currencyService;
  }

  @Autowired
  void setPortfolioRepo(PortfolioRepository portfolioRepository) {
    this.portfolioRepository = portfolioRepository;
  }

  public Iterable<Portfolio> save(Iterable<Portfolio> portfolios) {
    return portfolioRepository.saveAll(portfolios);
  }

  public Collection<Portfolio> getPortfolios() {
    Collection<Portfolio> results = new ArrayList<>();
    Iterable<Portfolio> portfolios = portfolioRepository.findAll();
    for (Portfolio portfolio : portfolios) {
      portfolio.setBase(currencyService.getBase());
      portfolio.setCurrency(currencyService.getCode(portfolio.getCurrency().getCode()));
      results.add(portfolio);
    }
    return results;
  }
}
