package com.beancounter.marketdata.trn;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
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
  private TrnRepository trnRepository;
  private TrnAdapter trnAdapter;

  TrnService(TrnRepository trnRepository,
             TrnAdapter trnAdapter) {
    this.trnRepository = trnRepository;
    this.trnAdapter = trnAdapter;
  }

  public TrnResponse save(Portfolio portfolio, TrnRequest trnRequest) {
    log.info("Received request to write {} transactions {}",
        trnRequest.getData().size(), portfolio.getCode());
    TrnResponse results = trnAdapter.convert(portfolio, trnRequest);
    Iterable<Trn> saved = trnRepository.saveAll(results.getTrns());
    Collection<Trn> trns = new ArrayList<>();
    saved.forEach(trns::add);
    results.setTrns(trns);
    log.info("Wrote {} transactions", results.getTrns().size());
    return results;
  }

  public TrnResponse find(Portfolio portfolio, TrnId trnId) {
    Optional<Trn> found = trnRepository.findById(trnId);
    return found.map(transaction -> hydrate(portfolio, transaction))
        .orElseGet(() -> TrnResponse.builder().build());
  }

  public TrnResponse find(Portfolio portfolio, String assetId) {
    Collection<Trn> results = trnRepository.findByPortfolioIdAndAssetId(portfolio.getId(),
        assetId,
        Sort.by("asset.code")
            .and(Sort.by("tradeDate")));
    log.debug("Found {} for portfolio {} and asset {}",
        results.size(),
        portfolio.getCode(),
        assetId
    );
    return hydrate(portfolio, results);
  }

  public TrnResponse find(Portfolio portfolio) {
    Collection<Trn> results = trnRepository.findByPortfolioId(portfolio.getId(),
        Sort.by("asset.code")
            .and(Sort.by("tradeDate")));
    log.debug("Found {} for portfolio {}", results.size(), portfolio.getCode());
    return hydrate(portfolio, results);
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

  private TrnResponse hydrate(Portfolio portfolio, Iterable<Trn> trns) {
    TrnResponse trnResponse = TrnResponse.builder()
        .build();
    trnResponse.addPortfolio(portfolio);
    Collection<Trn> trnCollection = new ArrayList<>();
    for (Trn trn : trns) {
      trnCollection.add(trn);
      trn.setAsset(trnAdapter.hydrate(trn.getAsset()));
      trn.setCashAsset(trnAdapter.hydrate(trn.getCashAsset()));
    }
    trnResponse.setTrns(trnCollection);
    return trnResponse;
  }

  private TrnResponse hydrate(Portfolio portfolio, Trn trn) {
    TrnResponse trnResponse = TrnResponse.builder()
        .trns(Collections.singleton(trn))
        .build();
    trnResponse.addPortfolio(portfolio);
    return trnResponse;
  }
}
