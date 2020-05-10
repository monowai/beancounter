package com.beancounter.marketdata.assets;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetUpdateResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.input.AssetInput;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.KeyGenUtils;
import com.beancounter.marketdata.markets.MarketService;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import javax.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class AssetService implements com.beancounter.client.AssetService {
  private final EnrichmentFactory enrichmentFactory;
  private AssetRepository assetRepository;
  private MarketService marketService;

  AssetService(EnrichmentFactory enrichmentFactory) {
    this.enrichmentFactory = enrichmentFactory;
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
      // Is the market supported?
      Market market = marketService.getMarket(assetInput.getMarket(), false);
      String defaultName = null;
      if (assetInput.getName() != null) {
        defaultName = assetInput.getName().replace("\"", "");
      }

      // Enrich missing attributes
      Asset asset = enrichmentFactory.getEnricher(market).enrich(
          market,
          assetInput.getCode(),
          defaultName);

      if (asset == null) {
        // User Defined Asset?
        asset = Asset.builder().build();
        asset.setId(KeyGenUtils.format(UUID.randomUUID()));
        asset.setCode(assetInput.getCode().toUpperCase());
        asset.setMarketCode(market.getCode());
        asset.setMarket(market);
        asset.setName(defaultName);
      } else {
        // Market Listed
        asset.setMarket(market);
        asset.setId(KeyGenUtils.format(UUID.randomUUID()));
      }
      return hydrateAsset(assetRepository.save(asset));

    }
    return backFillMissingData(foundAsset);
  }

  public AssetUpdateResponse process(AssetRequest asset) {
    Map<String, Asset> assets = new HashMap<>();
    for (String key : asset.getData().keySet()) {
      assets.put(
          key,
          this.create(asset.getData().get(key))
      );
    }
    return AssetUpdateResponse.builder().data(assets).build();
  }

  public Asset find(String marketCode, String code) {
    Asset asset = findLocally(marketCode, code);
    if (asset == null) {
      Market market = marketService.getMarket(marketCode);
      asset = enrichmentFactory.getEnricher(market).enrich(market, code, null);
      if (asset == null) {
        throw new BusinessException(String.format("No asset found for %s:%s", marketCode, code));
      }
      if (!marketCode.equalsIgnoreCase("MOCK")) {
        if (asset.getId() == null) {
          asset.setId(KeyGenUtils.format(UUID.randomUUID()));
        }
        asset = assetRepository.save(asset);
      }

    }
    return hydrateAsset(asset);
  }

  public Asset find(String id) {
    Optional<Asset> result = assetRepository.findById(id).map(this::hydrateAsset);
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

  public Asset backFillMissingData(Asset asset) {
    AssetEnricher enricher = enrichmentFactory.getEnricher(asset.getMarket());
    if (enricher.canEnrich(asset)) {
      Asset enriched = enricher.enrich(asset.getMarket(), asset.getCode(), asset.getName());

      if (enriched != null) {
        enriched.setId(asset.getId());
        assetRepository.save(enriched);
        return enriched;
      }
    }
    return asset;
  }

  public Asset hydrateAsset(Asset asset) {
    assert asset != null;
    asset.setMarket(marketService.getMarket(asset.getMarketCode()));
    return asset;
  }

  public Stream<Asset> findAllAssets() {
    return assetRepository.findAllAssets();
  }
}
