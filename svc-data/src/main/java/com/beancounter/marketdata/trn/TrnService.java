package com.beancounter.marketdata.trn;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TrnId;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.marketdata.assets.AssetService;
import java.util.Collection;
import java.util.Collections;
import java.util.Optional;
import javax.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@Transactional
public class TrnService {
  private TrnRepository trnRepository;
  private AssetService assetService;

  TrnService(TrnRepository trnRepository) {
    this.trnRepository = trnRepository;
  }

  @Autowired
  void setAssetService(AssetService assetService) {
    this.assetService = assetService;
  }

  public TrnResponse save(Portfolio portfolio, TrnRequest trnRequest) {
    log.info("Received request to write {} transactions {}",
        trnRequest.getTrns().size(), portfolio.getCode());
    Iterable<Trn> saved = trnRepository.saveAll(trnRequest.getTrns());
    log.info("Wrote {} transactions", trnRequest.getTrns().size());
    return getTrnResponse(portfolio, saved);
  }

  public TrnResponse find(Portfolio portfolio, TrnId trnId) {
    Optional<Trn> found = trnRepository.findById(trnId);
    return found.map(transaction -> getTrnResponse(portfolio, transaction))
        .orElseGet(() -> TrnResponse.builder().build());
  }

  public TrnResponse find(Portfolio portfolio) {
    Collection<Trn> results = trnRepository.findByPortfolioId(portfolio.getId(),
        Sort.by("asset.code")
            .and(Sort.by("tradeDate")));
    log.debug("Found {} for portfolio {}", results.size(), portfolio.getCode());
    return getTrnResponse(portfolio, results);
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

  private TrnResponse getTrnResponse(Portfolio portfolio, Iterable<Trn> trns) {
    TrnResponse trnResponse = TrnResponse.builder()
        .build();
    trnResponse.addPortfolio(portfolio);
    Asset asset = null;
    for (Trn trn : trns) {
      if (asset == null || !trn.getAsset().getId().equals(asset.getId())) {
        asset = assetService.find(trn.getAsset().getId());
      }
      trn.setAsset(asset);
      trnResponse.getTrns().add(trn);
    }
    return trnResponse;
  }

  private TrnResponse getTrnResponse(Portfolio portfolio, Trn trn) {
    trn.setAsset(assetService.find(trn.getAsset().getId()));
    TrnResponse trnResponse = TrnResponse.builder()
        .trns(Collections.singleton(trn))
        .build();
    trnResponse.addPortfolio(portfolio);
    return trnResponse;
  }
}
