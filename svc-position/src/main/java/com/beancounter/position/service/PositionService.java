package com.beancounter.position.service;

import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Portfolio;
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

  public PositionResponse build(PositionRequest positionRequest) {
    Portfolio portfolio = bcService.getPortfolioById(positionRequest.getPortfolioId());
    return build(portfolio, positionRequest);
  }

  /**
   * {@inheritDoc}
   *
   * @return
   */
  public PositionResponse build(Portfolio portfolio, PositionRequest positionRequest) {

    Positions positions = null;
    for (Transaction transaction : positionRequest.getTransactions()) {
      setObjects(portfolio, transaction);
      if (positions == null) {
        positions = new Positions(portfolio);
      }
      positions.add(accumulator.accumulate(transaction, positions));
    }
    return PositionResponse.builder().data(positions).build();
  }

  public void setObjects(Portfolio portfolio, Transaction transaction) {
    transaction.setCashCurrency(bcService.getCurrency(transaction.getCashCurrency()));
    transaction.setTradeCurrency(
        bcService.getCurrency(transaction.getAsset().getMarket().getCurrency()));
    transaction.setPortfolio(portfolio);
  }
}
