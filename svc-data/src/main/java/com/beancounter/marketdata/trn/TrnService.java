package com.beancounter.marketdata.trn;

import com.beancounter.common.contracts.PortfoliosResponse;
import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Trn;
import com.beancounter.common.utils.DateUtils;
import com.beancounter.marketdata.portfolio.PortfolioService;
import java.time.LocalDate;
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
  private final DateUtils dateUtils = new DateUtils();

  TrnService(TrnRepository trnRepository,
             TrnAdapter trnAdapter,
             PortfolioService portfolioService) {
    this.trnRepository = trnRepository;
    this.portfolioService = portfolioService;
    this.trnAdapter = trnAdapter;
  }

  public PortfoliosResponse findWhereHeld(String assetId, LocalDate tradeDate) {
    if (tradeDate == null) {
      tradeDate = dateUtils.getDate(dateUtils.today());
    }
    Collection<Portfolio> portfolios = trnRepository
        .findDistinctPortfolioByAssetIdAndTradeDate(assetId, tradeDate);
    log.info("Found {}", portfolios.size());
    return PortfoliosResponse.builder().data(portfolios).build();
  }

  public TrnResponse getPortfolioTrn(Portfolio portfolio, String trnId) {
    Optional<Trn> trn = trnRepository.findByPortfolioIdAndId(portfolio.getId(), trnId);
    Optional<TrnResponse> result = trn.map(
        transaction -> hydrate(Collections.singleton(transaction)));

    if (result.isEmpty()) {
      throw new BusinessException(String.format("Trn %s not found", trnId));
    }
    return result.get();

  }

  public TrnResponse save(Portfolio portfolio, TrnRequest trnRequest) {
    TrnResponse trnResponse = trnAdapter.convert(portfolio, trnRequest);
    Iterable<Trn> saved = trnRepository.saveAll(trnResponse.getData());
    Collection<Trn> trns = new ArrayList<>();
    saved.forEach(trns::add);
    trnResponse.setData(trns);
    log.trace("Wrote {}/{} transactions for {}",
        trnResponse.getData().size(), trnRequest.getData().size(), portfolio.getCode());

    return trnResponse;
  }

  /**
   * Display order.
   *
   * @param portfolio fully qualified portfolio the caller is authorised to view
   * @param assetId filter by pk
   * @return Transactions in display order that is friendly for viewing.
   */
  public TrnResponse findByPortfolioAsset(Portfolio portfolio, String assetId) {
    Collection<Trn> results = trnRepository
        .findByPortfolioIdAndAssetId(
            portfolio.getId(),
            assetId, Sort.by("asset.code")
                .and(Sort.by("tradeDate").descending()));

    log.debug("Found {} for portfolio {} and asset {}",
        results.size(),
        portfolio.getCode(),
        assetId
    );
    return hydrate(results, true);

  }

  /**
   * Processing order.
   *
   * @param portfolio trusted
   * @param assetId   filter by pk
   * @param tradeDate until this date inclusive
   * @return transactions that can be accumulated into a position
   */
  public TrnResponse findByPortfolioAsset(
      Portfolio portfolio,
      String assetId,
      LocalDate tradeDate) {

    Collection<Trn> results = trnRepository
        .findByPortfolioIdAndAssetIdUpTo(
            portfolio.getId(),
            assetId,
            tradeDate);

    log.debug("Found {} for portfolio {} and asset {}",
        results.size(),
        portfolio.getCode(),
        assetId
    );
    return hydrate(results, false);
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
    return hydrate(trns, true);
  }

  private TrnResponse hydrate(Iterable<Trn> trns, boolean secure) {
    Collection<Trn> results = new ArrayList<>();
    for (Trn trn : trns) {
      boolean add = (!secure || portfolioService.canView(trn.getPortfolio()));
      if (add) {
        results.add(setAssets(trn));
      }
    }
    if (results.isEmpty()) {
      return TrnResponse.builder().build(); // Empty
    }
    return TrnResponse.builder().data(results).build();
  }

}
