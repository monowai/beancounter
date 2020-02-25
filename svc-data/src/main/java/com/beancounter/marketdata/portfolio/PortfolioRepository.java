package com.beancounter.marketdata.portfolio;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface PortfolioRepository extends CrudRepository<Portfolio, String> {
  Optional<Portfolio> findByCodeAndOwner(String code, SystemUser systemUser);

  Iterable<Portfolio> findByOwner(SystemUser systemUser);
}
