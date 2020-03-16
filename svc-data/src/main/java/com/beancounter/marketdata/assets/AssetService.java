package com.beancounter.marketdata.assets;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.exception.BusinessException;
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

  public Asset create(Asset asset) {
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

    }
    return foundAsset;
  }

  public AssetResponse process(AssetRequest asset) {
    Map<String, Asset> assets = new HashMap<>();
    for (String key : asset.getData().keySet()) {
      assets.put(key, create(asset.getData().get(key)));
    }
    return AssetResponse.builder().data(assets).build();
  }

  public Asset find(String marketCode, String code) {
    Optional<Asset> optionalAsset = assetRepository
        .findByMarketCodeAndCode(marketCode.toUpperCase(), code.toUpperCase());
    return optionalAsset.map(this::hydrateAsset).orElse(null);
  }

  public Asset find(String id) {
    Optional<Asset> result = assetRepository
        .findById(id).map(this::hydrateAsset);
    if ( result.isPresent()) {
      return result.get();
    }
    throw new BusinessException(String.format("Asset.id %s not found", id));
  }

  private Asset hydrateAsset(Asset asset) {
    asset.setMarket(marketService.getMarket(asset.getMarketCode()));
    return asset;
  }
}
