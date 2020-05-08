package com.beancounter.marketdata.providers;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import java.time.LocalDate;

public interface DataProviderConfig {
  /**
   * MarketDataProviders have difference API restrictions. Number of assets in a single call is
   * one of them.
   *
   * @return Number of Assets to request in a single call.
   */
  Integer getBatchSize();

  LocalDate getMarketDate(Market market, String date);

  String getPriceCode(Asset asset);
}
