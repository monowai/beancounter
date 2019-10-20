package com.beancounter.common.utils;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import lombok.NonNull;

/**
 * Encapsulates routines to assist with asset keys and objects.
 *
 * @author mikeh
 * @since 2019-02-24
 */
public class AssetUtils {

  /**
   * Takes a valid asset and returns a key for it.
   *
   * @param asset valid Asset
   * @return string that can be used to pull the asset from a map
   */
  public static String toKey(@NonNull Asset asset) {
    return asset.getCode() + ":" + asset.getMarket().getCode();
  }

  /**
   * Takes an Asset key and returns an AssetObject.
   *
   * @param key result of parseKey(Asset)
   * @return an Asset
   */
  public static Asset fromKey(@NonNull String key) {
    assert key != null;
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
  public static Asset getAsset(@NonNull String assetCode, @NonNull String marketCode) {
    if (assetCode == null || marketCode == null) {
      throw new BusinessException("Both asset and market code must be supplied");
    }
    return getAsset(assetCode, Market.builder()
        .code(marketCode)
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
  public static Asset getAsset(@NonNull String assetCode, @NonNull Market market) {
    if (assetCode == null || market == null) {
      throw new BusinessException("Both asset and market code must be supplied");
    }
    return Asset.builder().code(assetCode)
        .market(market)
        .build();
  }
}
