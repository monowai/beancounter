package com.beancounter.marketdata.providers;

import com.beancounter.common.model.Market;

public interface DataProviderConfig {
  /**
   * MarketDataProviders have difference API restrictions. Number of assets in a single call is
   * one of them.
   *
   * @return Number of Assets to request in a single call.
   */
  Integer getBatchSize();

  /**
   * MarketDataProviders often have difference ways of handling Market Codes.
   *
   * @param market BeanCounter view of the Market Code
   * @return DataProvider view of the same MarketCode
   */
  String translateMarketCode(Market market);

  String getMarketDate(Market market, String date);
}
