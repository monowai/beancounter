package com.beancounter.marketdata.service;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.model.MarketData;
import java.util.Collection;

/**
 * Standard interface to retrieve MarketData information from an implementor.
 *
 * @author mikeh
 * @since 2019-01-27
 */
public interface MarketDataProvider {
  MarketData getCurrent(Asset asset);

  Collection<MarketData> getCurrent(Collection<Asset> assets);

  /**
   * Convenience function to return the ID.
   * @return Unique Id of the MarketDataProvider
   */
  String getId();

  /**
   * MarketDataProviders have difference API restrictions. Number of assets in a single call is
   * one of them.
   *
   * @return Number of Assets to request in a single call.
   */
  Integer getBatchSize();

  Boolean isMarketSupported(Market market);

  /**
   * MarketDataProviders often have difference ways of handling Market Codes.
   * 
   * @param market BeanCounter view of the Market Code
   * @return DataProvider view of the same MarketCode
   */
  String getMarketProviderCode(Market market);

  /**
   * Last price date on the Market.
   * @return date to use for retrieval ops
   */
  String getDate();

}
