package com.beancounter.marketdata.service;

import com.beancounter.common.contracts.PriceRequest;
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
  MarketData getPrices(Asset asset);

  Collection<MarketData> getPrices(PriceRequest priceRequest);

  /**
   * Convenience function to return the ID.
   *
   * @return Unique Id of the MarketDataProvider
   */
  String getId();

  boolean isMarketSupported(Market market);
}