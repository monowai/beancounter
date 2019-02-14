package com.beancounter.position.service;

import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.model.Positions;
import java.util.Collection;

/**
 * Supports various calls to get Positrion related data.
 *
 * @author mikeh
 * @since 2019-01-27
 */


public interface Position {

  MarketData getPrice(String assetId);

  /**
   * Return the position collection from a collection of transactions.
   * @param transactions to return
   * @return computed stock positions
   */
  Positions getPositions(Collection<Transaction> transactions);
}
