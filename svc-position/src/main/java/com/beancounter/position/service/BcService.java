package com.beancounter.position.service;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.FxRequest;
import com.beancounter.common.contracts.FxResponse;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.position.integration.BcGateway;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class BcService {

  private BcGateway bcGateway;
  private Map<String, Currency> currencyMap = new HashMap<>();

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

  @EventListener(ApplicationReadyEvent.class)
  public Map<String, Currency> getCurrencies() {
    if (currencyMap.isEmpty()) {
      CurrencyResponse response = bcGateway.getCurrencies();
      currencyMap.putAll(response.getData());
    }
    return currencyMap;
  }


  public Currency getCurrency(Currency currency) {
    if (currency == null) {
      return null;
    }
    return currencyMap.get(currency.getCode());
  }

}
