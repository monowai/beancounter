package com.beancounter.marketdata.trn;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.marketdata.portfolio.PortfolioService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
public class TrnService {
  private final TrnRepository trnRepository;
  private final TrnAdapter trnAdapter;
  private final PortfolioService portfolioService;

  TrnService(TrnRepository trnRepository,
             TrnAdapter trnAdapter,
             PortfolioService portfolioService) {
    this.trnRepository = trnRepository;
    this.portfolioService = portfolioService;
    this.trnAdapter = trnAdapter;
  }

  public TrnResponse save(Portfolio portfolio, TrnRequest trnRequest) {
    TrnResponse results = trnAdapter.convert(portfolio, trnRequest);
    Iterable<Trn> saved = trnRepository.saveAll(results.getData());
    Collection<Trn> trns = new ArrayList<>();
    saved.forEach(trns::add);
    results.setData(trns);
    log.trace("Wrote {}/{} transactions for {}",
        results.getData().size(), trnRequest.getData().size(), portfolio.getCode());

    return results;
  }

  public TrnResponse find(Portfolio portfolio, String trnId) {
    Optional<Trn> trn = trnRepository.findByPortfolioIdAndId(portfolio.getId(), trnId);
    Optional<TrnResponse> result = trn.map(
        transaction -> hydrate(Collections.singleton(transaction)));

    if (result.isEmpty()) {
      throw new BusinessException(String.format("Trn %s not found", trnId));
    }
    return result.get();

  }


  public TrnResponse findByPortfolioAsset(Portfolio portfolio, String assetId) {
    Collection<Trn> results = trnRepository.findByPortfolioIdAndAssetId(portfolio.getId(),
        assetId,
        Sort.by("asset.code")
            .and(Sort.by("tradeDate").descending()));
    log.debug("Found {} for portfolio {} and asset {}",
        results.size(),
        portfolio.getCode(),
        assetId
    );
    return hydrate(results);
  }

  public TrnResponse findForPortfolio(Portfolio portfolio) {
    Collection<Trn> results = trnRepository.findByPortfolioId(portfolio.getId(),
        Sort.by("asset.code")
            .and(Sort.by("tradeDate")));
    log.debug("trns: {}, portfolio: {}", results.size(), portfolio.getCode());
    return hydrate(results);
  }

  /**
   * Purge transactions for a portfolio.
   *
   * @param portfolio portfolio owned by the caller
   * @return number of deleted transactions
   */
  public long purge(Portfolio portfolio) {
    return trnRepository.deleteByPortfolioId(portfolio.getId());
  }

  private Trn setAssets(Trn trn) {
    trn.setAsset(trnAdapter.hydrate(trn.getAsset()));
    trn.setCashAsset(trnAdapter.hydrate(trn.getCashAsset()));
    return trn;
  }

  private TrnResponse hydrate(Iterable<Trn> trns) {
    Collection<Trn> trnCollection = new ArrayList<>();
    for (Trn trn : trns) {
      if (portfolioService.canView(trn.getPortfolio())) {
        trnCollection.add(setAssets(trn));
      }
    }
    if (trnCollection.isEmpty()) {
      return TrnResponse.builder().build(); // Empty
    }
    return TrnResponse.builder().data(trnCollection).build();
  }

}
