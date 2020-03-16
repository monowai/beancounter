package com.beancounter.marketdata.trn;

import com.beancounter.common.identity.TrnId;
import com.beancounter.common.model.Trn;
import java.util.Collection;
import org.springframework.data.domain.Sort;
import org.springframework.data.repository.CrudRepository;

public interface TrnRepository extends CrudRepository<Trn, TrnId> {
  Collection<Trn> findByPortfolioId(String portfolioId, Sort sort);

  Collection<Trn> findByPortfolioIdAndAssetId(String portfolioId, String assetId, Sort sort);

  long deleteByPortfolioId(String portfolioId);
}
