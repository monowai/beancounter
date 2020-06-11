package com.beancounter.marketdata.trn;

import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

public interface TrnRepository extends CrudRepository<Trn, CallerRef> {
  Collection<Trn> findByPortfolioId(String portfolioId, Sort sort);

  Collection<Trn> findByPortfolioIdAndAssetId(String portfolioId, String assetId, Sort sort);

  Optional<Trn> findByCallerRef(CallerRef callerRef);

  long deleteByPortfolioId(String portfolioId);

  Optional<Trn> findByPortfolioIdAndId(String portfolioId, String trnId);

  @Query("select distinct t.portfolio from Trn t where t.asset.id = ?1 and t.tradeDate <= ?2")
  Collection<Portfolio> findDistinctPortfolioByAssetIdAndTradeDate(
      String assetId, LocalDate tradeDate);
}
