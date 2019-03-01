package com.beancounter.marketdata.service;

import com.beancounter.common.model.Asset;
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

  String getId();
}
