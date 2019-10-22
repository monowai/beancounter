package com.beancounter.position.service;

import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Returns collections of positions for a Portfolio.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@Service
public class PositionService implements Position {

  private Accumulator accumulator;

  @Autowired
  PositionService(Accumulator accumulator) {
    this.accumulator = accumulator;
  }

  /**
   * {@inheritDoc}
   *
   * @return
   */
  public PositionResponse build(Collection<Transaction> transactions) {
    Positions positions = null;
    // ToDo: PostionRequestRequest
    for (Transaction transaction : transactions) {
      if (positions == null) {
        positions = new Positions(transaction.getPortfolio());
      }
      positions.add(accumulator.accumulate(transaction, positions.get(transaction.getAsset())));
    }
    return PositionResponse.builder().data(positions).build();
  }
}
