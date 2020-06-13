package com.beancounter.marketdata.portfolio;

import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.SystemUser;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface PortfolioRepository extends CrudRepository<Portfolio, String> {
  Optional<Portfolio> findByCodeAndOwner(String code, SystemUser systemUser);

  Iterable<Portfolio> findByOwner(SystemUser systemUser);

  @Query("select distinct t.portfolio from Trn t where t.asset.id = ?1 and t.tradeDate <= ?2")
  Collection<Portfolio> findDistinctPortfolioByAssetIdAndTradeDate(
      String assetId, LocalDate tradeDate);

}
