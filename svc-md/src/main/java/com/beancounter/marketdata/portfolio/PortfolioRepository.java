package com.beancounter.marketdata.portfolio;

import com.beancounter.common.model.Portfolio;
import org.springframework.data.repository.CrudRepository;

public interface PortfolioRepository extends CrudRepository<Portfolio, String> {
  Portfolio findByCode(String code);
}
