package com.beancounter.marketdata.assets;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.exception.BusinessException;
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
  private final FigiProxy figiProxy;
  private AssetRepository assetRepository;
  private MarketService marketService;

  AssetService(FigiProxy figiProxy) {
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

  public Asset create(Asset asset) {
    Asset foundAsset = findLocally(
        asset.getMarket().getCode().toUpperCase(),
        asset.getCode().toUpperCase());

    if (foundAsset == null) {
      Market market = marketService.getMarket(asset.getMarket().getCode());

      // Find @Bloomberg
      Asset figiAsset = findExternally(asset.getMarket().getCode(), asset.getCode());

      if (figiAsset == null) {
        // User Defined Asset?
        asset.setId(KeyGenUtils.format(UUID.randomUUID()));
        asset.setCode(asset.getCode().toUpperCase());
        asset.setMarketCode(market.getCode());
        if (asset.getName() != null) {
          asset.setName(asset.getName().replace("\"", ""));
        }
        foundAsset = assetRepository.save(asset);
      } else {
        // Market Listed
        figiAsset.setId(KeyGenUtils.format(UUID.randomUUID()));
        foundAsset = assetRepository.save(figiAsset);
      }
      foundAsset.setMarket(market);


    }
    return foundAsset;
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
    Optional<Asset> result = assetRepository
        .findById(id).map(this::hydrateAsset);
    if (result.isPresent()) {
      return result.get();
    }
    throw new BusinessException(String.format("Asset.id %s not found", id));
  }

  public Asset findLocally(String marketCode, String code) {
    // Search Local
    Optional<Asset> optionalAsset =
        assetRepository.findByMarketCodeAndCode(marketCode.toUpperCase(), code.toUpperCase());
    return optionalAsset.map(this::hydrateAsset).orElse(null);
  }

  private Asset findExternally(String marketCode, String code) {
    if (marketCode.equalsIgnoreCase("MOCK")) {
      return null;
    }
    return figiProxy.find(marketCode, code);
  }

  private Asset hydrateAsset(Asset asset) {
    asset.setMarket(marketService.getMarket(asset.getMarketCode()));
    return asset;
  }
}
