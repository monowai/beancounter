package com.beancounter.common.utils;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Currency;
import com.beancounter.common.model.Market;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

  private final ObjectMapper objectMapper = new ObjectMapper();

  /**
   * Takes a valid asset and returns a key for it.
   *
   * @param asset valid Asset
   * @return string that can be used to pull the asset from a map
   */
  public String toKey(@NonNull Asset asset) {
    return toKey(asset.getCode(), asset.getMarket().getCode());
  }

  public String toKey(@NonNull AssetInput asset) {
    return toKey(asset.getCode(), asset.getMarket());
  }

  public String toKey(@NonNull String asset, @NonNull String market) {
    return asset + ":" + market;
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
    return getAsset(marketAsset[1], marketAsset[0]);
  }

  /**
   * Helper to create a simple Asset with a USD currency.
   *
   * @param marketCode marketCode
   * @param assetCode  assetCode
   * @return simple Asset.
   */
  public Asset getAsset(@NonNull String marketCode, @NonNull String assetCode) {
    Asset asset = getAsset(
        Market.builder()
            .code(marketCode.toUpperCase())
            .currency(Currency.builder().code("USD").build())
            .build(), assetCode
    );
    asset.setMarketCode(null);
    return asset;
  }

  /**
   * Asset on a market.
   *
   * @param market    market to return
   * @param assetCode asset.code
   * @return asset on a market
   */
  public Asset getAsset(@NonNull Market market, @NonNull String assetCode) {
    Asset asset = Asset.builder()
        .id(assetCode)
        .code(assetCode)
        .market(market)
        .name(assetCode)
        .marketCode(market.getCode().toUpperCase())
        .build();
    asset.setMarketCode(null);
    return asset;

  }

  public Map<String, Collection<Asset>> split(Collection<AssetInput> assets) {
    Map<String, Collection<Asset>> results = new HashMap<>();
    for (AssetInput input : assets) {
      Market market = input.getResolvedAsset().getMarket();
      Collection<Asset> marketAssets =
          results.computeIfAbsent(market.getCode(), k -> new ArrayList<>());
      marketAssets.add(input.getResolvedAsset());
    }
    return results;
  }

  /**
   * Helper for tests that returns the "serialized" view of an asset.
   *
   * @param market marketCode
   * @param code   assetCode
   * @return asset object with all @JsonIgnore fields applied
   * @throws JsonProcessingException error
   */
  public Asset getJsonAsset(String market, String code) throws JsonProcessingException {
    Asset asset = getAsset(market, code);
    return objectMapper.readValue(objectMapper.writeValueAsString(asset), Asset.class);
  }

  public AssetInput getAssetInput(String market, String code) {
    return AssetInput.builder()
        .market(market)
        .name(code)
        .code(code)
        .build();
  }

  public AssetInput getAssetInput(Asset asset) {
    return AssetInput.builder()
        .code(asset.getCode())
        .name(asset.getName())
        .market(asset.getMarket().getCode())
        .resolvedAsset(asset).build();
  }
}
