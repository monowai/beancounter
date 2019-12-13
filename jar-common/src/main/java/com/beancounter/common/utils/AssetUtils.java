package com.beancounter.common.utils;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Encapsulates routines to assist with asset keys and objects.
 *
 * @author mikeh
 * @since 2019-02-24
 */
@UtilityClass
public class AssetUtils {

  /**
   * Takes a valid asset and returns a key for it.
   *
   * @param asset valid Asset
   * @return string that can be used to pull the asset from a map
   */
  public String toKey(@NonNull Asset asset) {
    return asset.getCode() + ":" + asset.getMarket().getCode();
  }

  /**
   * Takes an Asset key and returns an AssetObject.
   *
   * @param key result of parseKey(Asset)
   * @return an Asset
   */
  public Asset fromKey(@NonNull String key) {
    String[] marketAsset = key.split(":");
    if (marketAsset.length != 2) {
      throw new BusinessException(String.format("Unable to parse the key %s", key));
    }
    return getAsset(marketAsset[0], marketAsset[1]);
  }

  /**
   * Helper to create a simple Asset with a USD currency.
   *
   * @param assetCode  assetCode
   * @param marketCode marketCode
   * @return simple Asset.
   */
  public Asset getAsset(@NonNull String assetCode, @NonNull String marketCode) {
    return getAsset(assetCode, Market.builder()
        .code(marketCode)
        .id(marketCode)
        .currency(Currency.builder().code("USD").build())
        .build());
  }

  /**
   * Asset on a market.
   *
   * @param assetCode asset.code
   * @param market    market to return
   * @return asset on a market
   */
  public Asset getAsset(@NonNull String assetCode, @NonNull Market market) {
    return Asset.builder()
        .id(assetCode)
        .code(assetCode)
        .market(market)
        .build();
  }

  public Map<String, Collection<Asset>> split(Collection<Asset> assets) {
    Map<String, Collection<Asset>> results = new HashMap<>();
    for (Asset asset : assets) {
      Market market = asset.getMarket();
      Collection<Asset> marketAssets =
          results.computeIfAbsent(market.getCode(), k -> new ArrayList<>());
      marketAssets.add(asset);
    }
    return results;
  }
}
