package com.beancounter.position.service;

import com.beancounter.client.PortfolioService;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PositionRequest;
import com.beancounter.common.contracts.PositionResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.CurrencyPair;
import com.beancounter.common.model.FxRate;
import com.beancounter.common.model.MarketData;
import com.beancounter.common.model.Portfolio;
import com.beancounter.common.model.Positions;
import com.beancounter.common.model.Trn;
import com.beancounter.position.accumulation.MarketValue;
import com.beancounter.position.model.FxReport;
import com.beancounter.position.model.ValuationData;
import com.beancounter.position.utils.FxUtils;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
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
  private AsyncMdService asyncMdService;

  PositionService(Accumulator accumulator,
                  PortfolioService portfolioService,
                  AsyncMdService asyncMdService
  ) {
    this.accumulator = accumulator;
    this.portfolioService = portfolioService;
    this.asyncMdService = asyncMdService;
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
