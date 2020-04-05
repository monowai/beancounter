package com.beancounter.marketdata.service;

import com.beancounter.common.contracts.PriceRequest;
import com.beancounter.common.contracts.PriceResponse;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.MarketData;
import java.util.ArrayList;
import java.util.Collection;
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
  public PriceResponse getPrice(Asset asset) {
    List<AssetInput> inputs = new ArrayList<>();
    inputs.add(AssetInput.builder().resolvedAsset(asset).build());
    PriceRequest priceRequest = PriceRequest.builder().assets(inputs).build();
    return getPrice(priceRequest);
  }

  /**
   * MarketData for a Collection of assets.
   *
   * @param priceRequest to query
   * @return results
   */
  public PriceResponse getPrice(PriceRequest priceRequest) {
    Map<String, Collection<Asset>> factories = splitProviders(priceRequest.getAssets());
    Collection<MarketData> results = new ArrayList<>();

    for (String dpId : factories.keySet()) {
      results.addAll(mdFactory.getMarketDataProvider(dpId)
          .getMarketData(priceRequest));
    }
    return PriceResponse.builder().data(results).build();
  }

  private Map<String, Collection<Asset>> splitProviders(Collection<AssetInput> assets) {
    Map<String, Collection<Asset>> results = new HashMap<>();

    for (AssetInput input : assets) {
      MarketDataProvider marketDataProvider = mdFactory.getMarketDataProvider(input.getResolvedAsset());
      Collection<Asset> mdpAssets = results.get(marketDataProvider.getId());
      if (mdpAssets == null) {
        mdpAssets = new ArrayList<>();
      }
      mdpAssets.add(input.getResolvedAsset());
      results.put(marketDataProvider.getId(), mdpAssets);
    }
    return results;
  }


}
