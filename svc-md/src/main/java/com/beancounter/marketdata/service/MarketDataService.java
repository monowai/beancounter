package com.beancounter.marketdata.service;

import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
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
  private MarketService marketService;

  @Autowired
  MarketDataService(MdFactory mdFactory, MarketService marketService) {
    this.mdFactory = mdFactory;
    this.marketService = marketService;
  }

  /**
   * Get the current MarketData values for the supplied Asset.
   *
   * @param asset to query
   * @return MarketData - Values will be ZERO if not found or an integration problem occurs
   */
  public MarketData getCurrent(Asset asset) {
    hydrateAsset(asset);
    return mdFactory.getMarketDataProvider(asset).getCurrent(asset);
  }

  /**
   * MarketData for a Collection of assets.
   *
   * @param assets to query
   * @return results
   */
  public PriceResponse getCurrent(Collection<Asset> assets) {

    for (Asset asset : assets) {
      hydrateAsset(asset);
    }
    Map<String, Collection<Asset>> factories = splitProviders(assets);
    Collection<MarketData> results = new ArrayList<>();

    for (String dpId : factories.keySet()) {
      results.addAll(mdFactory.getMarketDataProvider(dpId)
          .getCurrent(factories.get(dpId)));
    }
    return PriceResponse.builder().data(results).build();
  }

  private Map<String, Collection<Asset>> splitProviders(Collection<Asset> assets) {
    Map<String, Collection<Asset>> results = new HashMap<>();

    for (Asset asset : assets) {
      MarketDataProvider marketDataProvider = mdFactory.getMarketDataProvider(asset);
      Collection<Asset> mdpAssets = results.get(marketDataProvider.getId());
      if (mdpAssets == null) {
        mdpAssets = new ArrayList<>();
      }
      mdpAssets.add(asset);
      results.put(marketDataProvider.getId(), mdpAssets);
    }
    return results;
  }

  private void hydrateAsset(Asset asset) {
    asset.setMarket(marketService.getMarket(asset.getMarket().getCode()));
  }




}
