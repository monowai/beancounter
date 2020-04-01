package com.beancounter.marketdata.trn;

import com.beancounter.common.identity.CallerRef;
import com.beancounter.common.model.Trn;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;

public interface TrnRepository extends CrudRepository<Trn, CallerRef> {
  Collection<Trn> findByPortfolioId(String portfolioId, Sort sort);

  Collection<Trn> findByPortfolioIdAndAssetId(String portfolioId, String assetId, Sort sort);

  Optional<Trn> findByCallerRef(CallerRef callerRef);

  long deleteByPortfolioId(String portfolioId);

  Optional<Trn> findByPortfolioIdAndId(String portfolioId, String trnId);
}
