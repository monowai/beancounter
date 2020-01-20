package com.beancounter.marketdata.trn;

import com.beancounter.common.contracts.TrnRequest;
import com.beancounter.common.contracts.TrnResponse;
import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class TrnService {
  private TrnRepository trnRepository;

  @Autowired
  void setTrnRepository(TrnRepository trnRepository) {
    this.trnRepository = trnRepository;
  }

  public TrnResponse save(Portfolio portfolio, TrnRequest trnRequest) {
    Iterable<Transaction> saved = trnRepository.saveAll(trnRequest.getTransactions());
    return getTrnResponse(portfolio, saved);
  }

  public TrnResponse find(Portfolio portfolio, TransactionId transactionId) {
    Optional<Transaction> found = trnRepository.findById(transactionId);
    return found.map(transaction -> getTrnResponse(portfolio, transaction))
        .orElseGet(() -> TrnResponse.builder().build());
  }

  public TrnResponse find(Portfolio portfolio) {
    return getTrnResponse(portfolio, trnRepository.findByPortfolioId(portfolio.getId()));
  }

  private TrnResponse getTrnResponse(Portfolio portfolio, Iterable<Transaction> saved) {
    TrnResponse trnResponse = TrnResponse.builder()
        .build();
    trnResponse.addPortfolio(portfolio);
    for (Transaction transaction : saved) {
      trnResponse.getTransactions().add(transaction);
    }
    return trnResponse;
  }

  private TrnResponse getTrnResponse(Portfolio portfolio, Transaction trn) {
    TrnResponse trnResponse = TrnResponse.builder()
        .transactions(Collections.singleton(trn))
        .build();
    trnResponse.addPortfolio(portfolio);
    return trnResponse;
  }
}
