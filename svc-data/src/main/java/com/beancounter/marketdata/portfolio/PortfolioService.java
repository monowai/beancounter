package com.beancounter.marketdata.portfolio;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.currency.CurrencyService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PortfolioService {
  private CurrencyService currencyService;
  private PortfolioRepository portfolioRepository;

  PortfolioService(
      CurrencyService currencyService,
      PortfolioRepository portfolioRepository
  ) {
    this.currencyService = currencyService;
    this.portfolioRepository = portfolioRepository;
  }

  public Collection<Portfolio> save(SystemUser owner, Collection<Portfolio> portfolios) {
    verifyOwner(owner);
    Collection<Portfolio> results = new ArrayList<>();
    portfolioRepository.saveAll(prepare(owner, portfolios)).forEach(results::add);
    return results;
  }

  private void verifyOwner(SystemUser owner) {
    if (owner == null) {
      throw new BusinessException("Unable to identify the owner");
    }
    if (!owner.getActive()) {
      throw new BusinessException("User is not active");
    }
  }

  private Collection<Portfolio> prepare(SystemUser owner, Collection<Portfolio> portfolios) {
    for (Portfolio portfolio : portfolios) {
      portfolio.setCode(portfolio.getCode().toUpperCase());
      portfolio.setOwner(owner);
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
