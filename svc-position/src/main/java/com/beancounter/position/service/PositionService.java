package com.beancounter.position.service;

import com.beancounter.client.services.PortfolioService;
import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Returns collections of positions for a Portfolio.
 *
 * @author mikeh
 * @since 2019-02-01
 */
@Service
@Slf4j
public class PositionService implements Position {

  private Accumulator accumulator;
  private PortfolioService portfolioService;

  PositionService(Accumulator accumulator,
                  PortfolioService portfolioService
  ) {
    this.accumulator = accumulator;
    this.portfolioService = portfolioService;
  }

  public PositionResponse build(PositionRequest positionRequest) {
    return build(
        portfolioService.getPortfolioById(positionRequest.getPortfolioId()),
        positionRequest
    );
  }

  public PositionResponse build(Portfolio portfolio, PositionRequest positionRequest) {

    Positions positions = new Positions(portfolio);
    for (Trn trn : positionRequest.getTrns()) {
      positions.add(accumulator.accumulate(trn, positions));
    }
    return PositionResponse.builder().data(positions).build();
  }

}
