package com.beancounter.ingest.service;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StaticDataService {
  private Map<String, Market> marketMap = new HashMap<>();
  private Map<String, Currency> currencyMap = new HashMap<>();
  private BcGateway bcGateway;

  @Autowired
  void setBcGateway(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  @EventListener(ApplicationReadyEvent.class)
  public Map<String, Market> getMarkets() {
    if (marketMap.isEmpty()) {
      MarketResponse marketResponse = bcGateway.getMarkets();
      if ( marketResponse == null ) {
        log.info("No markets found");
        return new HashMap<>();
      }
      for (Market market : marketResponse.getData()) {
        marketMap.put(market.getCode(), market);
      }
    }
    return marketMap;
  }

  @EventListener(ApplicationReadyEvent.class)
  public Map<String, Currency> getCurrencies() {
    if (currencyMap.isEmpty()) {
      CurrencyResponse response = bcGateway.getCurrencies();
      if ( response == null ) {
        log.info("No currencies found");
        return new HashMap<>();
      }
      currencyMap.putAll(response.getData());
    }
    return currencyMap;
  }

}
