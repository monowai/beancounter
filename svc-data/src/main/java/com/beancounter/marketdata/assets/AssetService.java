package com.beancounter.marketdata.assets;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.markets.MarketService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssetService {
  private AssetRepository assetRepository;
  private MarketService marketService;

  @Autowired
  void setAssetRepository(AssetRepository assetRepository) {
    this.assetRepository = assetRepository;
  }

  @Autowired
  void setMarketService(MarketService marketService) {
    this.marketService = marketService;
  }

  public Asset upsert(Asset asset) {
    Asset foundAsset = find(
        asset.getMarket().getCode().toUpperCase(),
        asset.getCode().toUpperCase());

    if (foundAsset == null) {
      asset.setId(KeyGenUtils.format(UUID.randomUUID()));
      asset.setCode(asset.getCode().toUpperCase());
      Market market = marketService.getMarket(asset.getMarket().getCode());
      asset.setMarketCode(market.getCode());
      foundAsset = assetRepository.save(asset);
      foundAsset.setMarket(market);
      return foundAsset;
    } else {
      Asset updated = assetRepository.save(asset);
      updated.setMarket(marketService.getMarket(asset.getMarket().getCode()));
      return updated;
    }
  }

  public AssetResponse process(AssetRequest asset) {
    Map<String,Asset>assets = new HashMap<>();
    for (String key : asset.getAssets().keySet()) {
      assets.put(key, upsert(asset.getAssets().get(key)));
    }
    return AssetResponse.builder().assets(assets).build();
  }

  public Asset find(String marketCode, String code) {
    Asset asset = null;
    Optional<Asset> foundAsset = assetRepository
        .findByMarketCodeAndCode(marketCode.toUpperCase(), code.toUpperCase());
    if (foundAsset.isPresent()) {
      asset = foundAsset.get();
      asset.setMarket(marketService.getMarket(asset.getMarketCode()));
    }
    return asset;
  }
}
