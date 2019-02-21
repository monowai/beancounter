package com.beancounter.position.counter;

import com.beancounter.common.model.Transaction;
import com.beancounter.common.model.TrnType;
import org.springframework.stereotype.Component;

/**
 * Encapsulate configurable logic that determines various accumulation paths that affect values.
 *
 * @author mikeh
 * @since 2019-02-17
 */
//@ConfigurationProperties(prefix = "com.beancounter.transaction")
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
}
