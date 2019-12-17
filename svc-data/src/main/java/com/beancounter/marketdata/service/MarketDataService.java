package com.beancounter.marketdata.service;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
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
  public PriceResponse getPrice(Asset asset) {
    hydrateAsset(asset);
    List<Asset> assets = Collections.singletonList(asset);
    PriceRequest priceRequest = PriceRequest.builder().assets(assets).build();
    return getPrice(priceRequest);
  }

  /**
   * MarketData for a Collection of assets.
   *
   * @param priceRequest to query
   * @return results
   */
  public PriceResponse getPrice(PriceRequest priceRequest) {

    for (Asset asset : priceRequest.getAssets()) {
      hydrateAsset(asset);
    }
    Map<String, Collection<Asset>> factories = splitProviders(priceRequest.getAssets());
    Collection<MarketData> results = new ArrayList<>();

    for (String dpId : factories.keySet()) {
      results.addAll(mdFactory.getMarketDataProvider(dpId)
          .getPrices(priceRequest));
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
