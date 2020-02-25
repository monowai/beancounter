package com.beancounter.position.service;

import static java.util.concurrent.TimeUnit.SECONDS;

import com.beancounter.client.FxRateService;
import com.beancounter.client.PriceService;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.exception.SystemException;
import com.beancounter.position.model.ValuationData;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
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

  public ValuationData getValuationData(
      CompletableFuture<PriceResponse> futurePriceResponse,
      CompletableFuture<FxResponse> futureFxResponse) {

    PriceResponse priceResponse;
    FxResponse fxResponse;
    try {
      priceResponse = futurePriceResponse.get(30, SECONDS);
      fxResponse = futureFxResponse.get(30, SECONDS);
      return ValuationData.builder()
          .fxResponse(fxResponse)
          .priceResponse(priceResponse)
          .build();
    } catch (InterruptedException | ExecutionException e) {
      log.error(e.getMessage());
      throw new SystemException("Getting Market Data");
    } catch (TimeoutException e) {
      log.error(e.getMessage());
      throw new SystemException("Timeout getting market data");
    }
  }
}
