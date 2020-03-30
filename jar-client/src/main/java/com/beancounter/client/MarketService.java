package com.beancounter.client;

import com.beancounter.common.contracts.MarketResponse;
import com.beancounter.common.model.Market;

public interface MarketService {

  MarketResponse getMarkets();

  Market getMarket(String marketCode);
}
