package com.beancounter.marketdata.service;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import com.beancounter.marketdata.providers.mock.MockProviderService;
import com.beancounter.marketdata.providers.wtd.WtdProviderService;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Service container for MarketData information.
 *
 * @author mikeh
 * @since 2019-01-28
 */
@Service
public class MarketDataService {

  private MdFactory mdFactory;

  @Autowired
  MarketDataService(MdFactory mdFactory) {
    this.mdFactory = mdFactory;
  }

  /**
   * Get the current MarketData values for the supplied Asset.
   *
   * @param asset to query
   * @return MarketData - Values will be ZERO if not found or an integration problem occurs
   */
  public MarketData getCurrent(Asset asset) {
    if (asset.getMarket().getCode().equalsIgnoreCase("MOCK")) {
      return mdFactory.getMarketDataProvider(MockProviderService.ID).getCurrent(asset);
    }
    return mdFactory.getMarketDataProvider(WtdProviderService.ID).getCurrent(asset);
  }

  /**
   * MarketData for a Collection of assets.
   *
   * @param assets to query
   * @return results
   */
  public Collection<MarketData> getCurrent(Collection<Asset> assets) {
    // ToDo: Read a config that determines:
    //  * which provider to request prices from based on Market
    //  * Split incoming assets into MarketDataProvider buckets before requesting data
    for (Asset asset : assets) {
      if (asset.getMarket().getCode().equalsIgnoreCase(MockProviderService.ID)) {
        return mdFactory.getMarketDataProvider(MockProviderService.ID).getCurrent(assets);
      }
    }
    return mdFactory.getMarketDataProvider(WtdProviderService.ID).getCurrent(assets);
  }
}
