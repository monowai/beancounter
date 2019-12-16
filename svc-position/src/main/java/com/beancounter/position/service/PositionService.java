package com.beancounter.position.service;

import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Transaction;
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
  private BcService bcService;

  @Autowired
  void setPositionService(Accumulator accumulator) {
    this.accumulator = accumulator;
  }

  @Autowired
  void setBcService(BcService bcService) {
    this.bcService = bcService;
  }

  /**
   * {@inheritDoc}
   *
   * @return
   */
  public PositionResponse build(PositionRequest positionRequest) {
    Positions positions = null;
    for (Transaction transaction : positionRequest.getTransactions()) {
      setObjects(transaction);
      if (positions == null) {
        positions = new Positions(transaction.getPortfolio());
      }
      positions.add(accumulator.accumulate(transaction, positions));
    }
    return PositionResponse.builder().data(positions).build();
  }

  public void setObjects(Transaction transaction) {
    transaction.setCashCurrency(bcService.getCurrency(transaction.getCashCurrency()));
    transaction.setTradeCurrency(
        bcService.getCurrency(transaction.getAsset().getMarket().getCurrency()));
    transaction.getPortfolio().setBase(bcService.getCurrency(transaction.getPortfolio().getBase()));
    transaction.getPortfolio().setCurrency(
        bcService.getCurrency(transaction.getPortfolio().getCurrency()));
  }
}
