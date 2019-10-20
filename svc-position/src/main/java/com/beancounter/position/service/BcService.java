package com.beancounter.position.service;

import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.position.integration.BcGateway;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BcService {

  private BcGateway bcGateway;

  @Autowired
  BcService(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  @Async
  public CompletableFuture<PriceResponse> getMarketData(Collection<Asset> assets) {
    return CompletableFuture.completedFuture(bcGateway.getMarketData(assets));
  }

  @Async
  public CompletableFuture<FxResponse> getFxData(FxRequest fxRequest) {
    return CompletableFuture.completedFuture(bcGateway.getRates(fxRequest));
  }
}
