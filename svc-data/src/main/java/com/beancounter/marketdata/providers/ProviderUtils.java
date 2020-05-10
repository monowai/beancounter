package com.beancounter.marketdata.providers;

import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.service.MarketDataProvider;
import com.beancounter.marketdata.service.MdFactory;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProviderUtils {

  private final MdFactory mdFactory;

  @Autowired
  public ProviderUtils(MdFactory mdFactory) {
    this.mdFactory = mdFactory;
  }

  public Map<MarketDataProvider, Collection<Asset>> splitProviders(Collection<AssetInput> assets) {
    Map<MarketDataProvider, Collection<Asset>> results = new HashMap<>();

    for (AssetInput input : assets) {
      Market market;
      if (input.getResolvedAsset() != null) {
        market = input.getResolvedAsset().getMarket();
      } else {
        // Asset is not known locally, but may be valid
        market = Market.builder().code(input.getMarket()).build();
        Asset resolvedAsset = Asset.builder()
            .code(input.getCode())
            .name(input.getName())
            .market(market)
            .build();
        input.setResolvedAsset(resolvedAsset);
      }
      MarketDataProvider marketDataProvider = mdFactory.getMarketDataProvider(market);
      Collection<Asset> mdpAssets = results.get(marketDataProvider);
      if (mdpAssets == null) {
        mdpAssets = new ArrayList<>();
      }
      mdpAssets.add(input.getResolvedAsset());
      results.put(marketDataProvider, mdpAssets);
    }
    return results;
  }

  public Collection<AssetInput> getInputs(Collection<Asset> apiAssets) {
    Collection<AssetInput> results = new ArrayList<>();
    for (Asset apiAsset : apiAssets) {
      results.add(AssetInput.builder()
          .code(apiAsset.getCode())
          .market(apiAsset.getMarket().getCode())
          .resolvedAsset(apiAsset)
          .build());
    }
    return results;
  }
}
