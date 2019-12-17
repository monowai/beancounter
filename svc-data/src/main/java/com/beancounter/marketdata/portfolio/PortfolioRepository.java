package com.beancounter.marketdata.portfolio;

import com.beancounter.common.model.Portfolio;
import java.util.Optional;
import org.springframework.data.repository.CrudRepository;

public interface PortfolioRepository extends CrudRepository<Portfolio, String> {
  Optional<Portfolio> findByCode(String code);
}
