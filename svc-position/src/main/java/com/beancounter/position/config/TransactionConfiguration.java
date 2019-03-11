package com.beancounter.position.config;

import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import org.springframework.stereotype.Component;

/**
 * Encapsulate configurable logic that determines various accumulation paths that affect values.
 *
 * @author mikeh
 * @since 2019-02-17
 */
@Component
public class TransactionConfiguration {

  public boolean isPurchase(Transaction transaction) {
    return transaction.getTrnType().equals(TrnType.BUY);
  }

  public boolean isSale(Transaction transaction) {
    return transaction.getTrnType().equals(TrnType.SELL);
  }

  public boolean isSplit(Transaction transaction) {
    return transaction.getTrnType().equals(TrnType.SPLIT);
  }

  /**
   * Determine if the supplied transaction is a dividend.
   * @param transaction analyze
   * @return true if yes
   */
  public boolean isDividend(Transaction transaction) {
    return transaction.getTrnType() == TrnType.DIVI;
  }
}
