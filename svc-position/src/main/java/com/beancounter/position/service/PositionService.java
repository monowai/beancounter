package com.beancounter.position.service;

import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
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

    Positions positions = new Positions(portfolio);
    for (Trn trn : positionRequest.getTrns()) {
      positions.add(accumulator.accumulate(trn, positions));
    }
    return PositionResponse.builder().data(positions).build();
  }

}
