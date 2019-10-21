package com.beancounter.ingest.service;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Market;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class MarketService {
  private Map<String, Market> marketMap = new HashMap<>();
  private BcGateway bcGateway;

  @Autowired(required = false)
  void setBcGateway(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  @EventListener(ApplicationReadyEvent.class)
  public Map<String, Market> getMarkets() {
    if (marketMap == null || marketMap.isEmpty()) {
      MarketResponse marketResponse = bcGateway.getMarkets();
      for (Market market : marketResponse.getData()) {
        marketMap.put(market.getCode(), market);
      }
    }
    return marketMap;
  }

}
