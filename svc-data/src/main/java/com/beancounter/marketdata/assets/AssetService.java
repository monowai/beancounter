package com.beancounter.marketdata.assets;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.assets.figi.FigiProxy;
import com.beancounter.marketdata.markets.MarketService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssetService implements com.beancounter.client.AssetService {
  private FigiProxy figiProxy;
  private AssetRepository assetRepository;
  private MarketService marketService;

  @Autowired(required = false)
  void setFigiProxy(FigiProxy figiProxy) {
    this.figiProxy = figiProxy;
  }

  @Autowired
  void setAssetRepository(AssetRepository assetRepository) {
    this.assetRepository = assetRepository;
  }

  @Autowired
  void setMarketService(MarketService marketService) {
    this.marketService = marketService;
  }

  private Asset create(AssetInput assetInput) {
    Asset foundAsset = findLocally(
        assetInput.getMarket().toUpperCase(),
        assetInput.getCode().toUpperCase());

    if (foundAsset == null) {
      Market market = marketService.getMarket(assetInput.getMarket());

      // Find @Bloomberg
      Asset figiAsset = findExternally(assetInput.getMarket(), assetInput.getCode());

      if (figiAsset == null) {
        // User Defined Asset?
        Asset asset = Asset.builder().build();
        asset.setId(KeyGenUtils.format(UUID.randomUUID()));
        asset.setCode(assetInput.getCode().toUpperCase());
        asset.setMarketCode(market.getCode());
        if (assetInput.getName() != null) {
          asset.setName(assetInput.getName().replace("\"", ""));
        }
        foundAsset = assetRepository.save(asset);
      } else {
        // Market Listed
        figiAsset.setId(KeyGenUtils.format(UUID.randomUUID()));
        foundAsset = assetRepository.save(figiAsset);
      }
      foundAsset.setMarket(market);


    }
    return backFillMissingData(foundAsset.getId(), foundAsset);
  }

  public AssetUpdateResponse process(AssetRequest asset) {
    Map<String, Asset> assets = new HashMap<>();
    for (String key : asset.getData().keySet()) {
      assets.put(key, create(asset.getData().get(key)));
    }
    return AssetUpdateResponse.builder().data(assets).build();
  }

  public Asset find(String marketCode, String code) {
    Asset asset = findLocally(marketCode, code);
    if (asset == null) {
      asset = findExternally(marketCode, code);
      if (asset == null) {
        throw new BusinessException(String.format("No asset found for %s/%s", marketCode, code));
      }

    }
    return asset;
  }

  public Asset find(String id) {
    Optional<Asset> result = assetRepository.findById(id).map(this::hydrateAsset);
    if (result.isPresent()) {
      Asset asset = result.get();
      return backFillMissingData(id, asset);
    }
    throw new BusinessException(String.format("Asset.id %s not found", id));
  }

  private Asset backFillMissingData(String id, Asset asset) {
    if (asset.getName() == null) {
      Asset figiAsset = findExternally(asset.getMarket().getCode(), asset.getCode());
      if (figiAsset != null) {
        figiAsset.setId(id);
        assetRepository.save(figiAsset);
        return figiAsset;
      }
    }
    return asset;
  }

  public Asset findLocally(String marketCode, String code) {
    // Search Local
    Optional<Asset> optionalAsset =
        assetRepository.findByMarketCodeAndCode(marketCode.toUpperCase(), code.toUpperCase());
    return optionalAsset.map(this::hydrateAsset).orElse(null);
  }

  private Asset findExternally(String marketCode, String code) {
    if (figiProxy == null || marketCode.equalsIgnoreCase("MOCK")) {
      return null;
    }
    return figiProxy.find(marketCode, code);
  }

  private Asset hydrateAsset(Asset asset) {
    asset.setMarket(marketService.getMarket(asset.getMarketCode()));
    return asset;
  }
}
