package com.beancounter.position.service;

import com.beancounter.client.FxRateService;
import com.beancounter.client.PriceService;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class AsyncMdService {

  private PriceService priceService;
  private FxRateService fxRateService;

  @Autowired
  AsyncMdService(
      PriceService priceService,
      FxRateService fxRateService) {
    this.priceService = priceService;
    this.fxRateService = fxRateService;
  }

  @Async
  public CompletableFuture<PriceResponse> getMarketData(PriceRequest priceRequest) {
    return CompletableFuture.completedFuture(priceService.getPrices(priceRequest));
  }

  @Async
  public CompletableFuture<FxResponse> getFxData(FxRequest fxRequest) {
    return CompletableFuture.completedFuture(fxRateService.getRates(fxRequest));
  }

}
