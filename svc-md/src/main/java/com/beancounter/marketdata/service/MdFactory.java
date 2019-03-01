package com.beancounter.marketdata.service;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.providers.alpha.AlphaProviderService;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Return a MarketData provider from a registered collection.
 *
 * @author mikeh
 * @since 2019-03-01
 */
@Service
public class MdFactory {

  private Map<String, MarketDataProvider> providers = new HashMap<>();

  @Autowired
  MdFactory(MockProviderService mockProviderService, AlphaProviderService alphaProviderService) {
    providers.put(mockProviderService.getId().toUpperCase(), mockProviderService);
    providers.put(alphaProviderService.getId().toUpperCase(), alphaProviderService);
  }

  /**
   * Figures out how to locate a Market Data provider for the requested asset.
   * If one can't be found, then the MOCK provider is returned.
   *
   * @param asset who wants to know?
   * @return the provider that supports the asset
   */
  public MarketDataProvider getMarketDataProvider(Asset asset) {
    MarketDataProvider provider = resolveProvider(asset.getMarket());
    if (provider == null) {
      return providers.get("MOCK");
    }
    return provider;
  }

  private MarketDataProvider resolveProvider(Market market) {
    if (market.getCode().equalsIgnoreCase("NYSE")) {
      return providers.get("ALPHA");
    } else if (market.getCode().equalsIgnoreCase("NASDAQ")) {
      return providers.get("ALPHA");
    } else {
      return providers.get("MOCK");
    }
  }
}
