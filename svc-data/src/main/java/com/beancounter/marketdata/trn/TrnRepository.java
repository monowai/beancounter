package com.beancounter.marketdata.trn;

import com.beancounter.common.identity.TransactionId;
import com.beancounter.common.model.Transaction;
import java.util.Collection;
import org.springframework.data.repository.CrudRepository;

public interface TrnRepository extends CrudRepository<Transaction, TransactionId> {
  Collection<Transaction> findByPortfolioId(String portfolioId);
}
