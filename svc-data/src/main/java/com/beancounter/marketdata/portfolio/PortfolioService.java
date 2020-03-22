package com.beancounter.marketdata.portfolio;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.PortfolioInput;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import com.beancounter.marketdata.registration.SystemUserService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PortfolioService {
  private PortfolioRepository portfolioRepository;
  private SystemUserService systemUserService;
  private PortfolioInputAdapter portfolioInputAdapter;

  PortfolioService(
      PortfolioInputAdapter portfolioInputAdapter,
      PortfolioRepository portfolioRepository,
      SystemUserService systemUserService
  ) {
    this.portfolioRepository = portfolioRepository;
    this.systemUserService = systemUserService;
    this.portfolioInputAdapter = portfolioInputAdapter;
  }

  public Collection<Portfolio> save(Collection<PortfolioInput> portfolios) {
    SystemUser owner = getOrThrow();
    Collection<Portfolio> results = new ArrayList<>();
    portfolioRepository.saveAll(
        portfolioInputAdapter.prepare(owner, portfolios)).forEach(results::add);
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

  private SystemUser getOrThrow() {
    SystemUser systemUser = systemUserService.getActiveUser();
    verifyOwner(systemUser);
    return systemUser;

  }

  public boolean canView(Portfolio found) {
    SystemUser systemUser = getOrThrow();
    return found.getOwner().getId().equals(systemUser.getId());
  }

  public Collection<Portfolio> getPortfolios() {
    SystemUser systemUser = getOrThrow();
    Collection<Portfolio> results = new ArrayList<>();
    Iterable<Portfolio> portfolios = portfolioRepository.findByOwner(systemUser);
    for (Portfolio portfolio : portfolios) {
      results.add(portfolio);
    }
    return results;
  }

  public Portfolio find(String id) {
    Optional<Portfolio> found = portfolioRepository.findById(id);
    Portfolio portfolio = found.orElseThrow(()
        -> new BusinessException(String.format("Could not find a portfolio with ID %s", id)));

    if (canView(portfolio)) {
      return portfolio;
    }

    throw new BusinessException(String.format("Could not find a portfolio with ID %s", id));

  }

  public Portfolio findByCode(String code) {
    SystemUser systemUser = getOrThrow();
    log.debug("Searching on behalf of {}", systemUser.getId());
    Optional<Portfolio> found = portfolioRepository
        .findByCodeAndOwner(code.toUpperCase(), systemUser);
    Portfolio portfolio = found.orElseThrow(()
        -> new BusinessException(
        String.format("Could not find a portfolio with code %s for %s",
            code,
            systemUser.getId())));

    if (canView(portfolio)) {
      return portfolio;
    }
    throw new BusinessException(String.format("Could not find a portfolio with code %s", code));

  }

  public Portfolio update(String id, PortfolioInput portfolioInput) {
    Portfolio existing = find(id);
    portfolioInputAdapter.fromInput(portfolioInput, existing);
    return portfolioRepository.save(existing);
  }

  public void delete(String id) {
    Portfolio portfolio = find(id);
    if (portfolio != null) {
      portfolioRepository.delete(portfolio);
    }
  }
}
