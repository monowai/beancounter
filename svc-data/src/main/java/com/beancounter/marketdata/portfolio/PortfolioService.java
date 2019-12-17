package com.beancounter.marketdata.portfolio;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.marketdata.currency.CurrencyService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
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
    return portfolioRepository.saveAll(prepare(portfolios));
  }

  private Iterable<Portfolio> prepare(Iterable<Portfolio> portfolios) {
    for (Portfolio portfolio : portfolios) {
      portfolio.setCode(portfolio.getCode().toUpperCase());
    }
    return portfolios;
  }

  public Collection<Portfolio> getPortfolios() {
    Collection<Portfolio> results = new ArrayList<>();
    Iterable<Portfolio> portfolios = portfolioRepository.findAll();
    for (Portfolio portfolio : portfolios) {
      portfolio.setBase(currencyService.getCode(portfolio.getBase().getCode()));
      portfolio.setCurrency(currencyService.getCode(portfolio.getCurrency().getCode()));
      results.add(portfolio);
    }
    return results;
  }

  public Portfolio find(String id) {
    Optional<Portfolio> found = portfolioRepository.findById(id);
    if (found.isEmpty()) {
      throw new BusinessException(String.format("Could not find a portfolio with ID %s", id));
    }
    return found.get();

  }

  public Portfolio findByCode(String code) {
    Optional<Portfolio> found = portfolioRepository.findByCode(code.toUpperCase());
    if (found.isEmpty()) {
      throw new BusinessException(String.format("Could not find a portfolio with code %s", code));
    }
    return found.get();
  }
}
