package com.beancounter.ingest.service;

import com.beancounter.common.contracts.CurrencyResponse;
import com.beancounter.common.contracts.MarketResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BcService {
  private BcGateway bcGateway;

  @Autowired
  void setBcGateway(BcGateway bcGateway) {
    this.bcGateway = bcGateway;
  }

  public MarketResponse getMarkets() {
    return bcGateway.getMarkets();
  }

  public CurrencyResponse getCurrencies() {
    return bcGateway.getCurrencies();
  }
}
