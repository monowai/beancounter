package com.beancounter.position.service;

import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Transaction;
import com.beancounter.position.counter.Accumulator;
import com.beancounter.position.integration.MdIntegration;
import com.beancounter.position.model.Positions;
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

  private MdIntegration mdIntegration;
  private Accumulator accumulator;

  @Autowired
  PositionService(MdIntegration mdIntegration, Accumulator accumulator) {
    this.mdIntegration = mdIntegration;
    this.accumulator = accumulator;
  }

  @Override
  public MarketData getPrice(String assetId) {
    return mdIntegration.getMarketData(assetId);
  }


  /**
   * {@inheritDoc}
   */
  public Positions getPositions(Collection<Transaction> transactions) {
    Positions positions = new Positions(Portfolio.builder().code("Mike").build());

    for (Transaction transaction : transactions) {
      positions.add(accumulator.accumulate(transaction, positions.get(transaction.getAsset())));
    }
    return positions;
  }
}
