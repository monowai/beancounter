package com.beancounter.position.service;

import com.beancounter.auth.TokenHelper;
import com.beancounter.client.FxRateService;
import com.beancounter.client.PortfolioService;
import com.beancounter.client.PriceService;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Portfolio;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class BcService {

  private PriceService priceService;
  private FxRateService fxRateService;
  private PortfolioService portfolioService;
  private Map<String, Currency> currencyMap = new HashMap<>();

  @Autowired
  BcService(
      PriceService priceService,
      FxRateService fxRateService,
      PortfolioService portfolioService) {
    this.priceService = priceService;
    this.fxRateService = fxRateService;
    this.portfolioService = portfolioService;
  }

  @Async
  public CompletableFuture<PriceResponse> getMarketData(PriceRequest priceRequest) {
    return CompletableFuture.completedFuture(priceService.getPrices(priceRequest));
  }

  @Async
  public CompletableFuture<FxResponse> getFxData(FxRequest fxRequest) {
    return CompletableFuture.completedFuture(fxRateService.getRates(fxRequest));
  }

  public Portfolio getPortfolioById(String portfolioId) {
    Portfolio portfolio = portfolioService.getPortfolioById(portfolioId);
    if (portfolio == null) {
      throw new BusinessException(String.format("Unable to locate portfolio id [%s]", portfolioId));
    }
    return portfolio;
  }

}
