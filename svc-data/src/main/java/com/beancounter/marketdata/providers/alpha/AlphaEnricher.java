package com.beancounter.marketdata.providers.alpha;

import com.beancounter.common.contracts.AssetSearchResponse;
import com.beancounter.common.contracts.AssetSearchResult;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.assets.AssetEnricher;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class AlphaEnricher implements AssetEnricher {
  private final ObjectMapper objectMapper = new AlphaPriceAdapter().getAlphaMapper();
  private final AlphaConfig alphaConfig;
  private AlphaProxyCache alphaProxyCache;
  @Value("${beancounter.marketdata.provider.ALPHA.key:demo}")
  private String apiKey;

  AlphaEnricher(AlphaConfig alphaConfig) {
    this.alphaConfig = alphaConfig;
  }

  @Autowired(required = false)
  void setAlphaProxyCache(AlphaProxyCache alphaProxyCache) {
    this.alphaProxyCache = alphaProxyCache;
  }

  @SneakyThrows
  @Override
  public Asset enrich(Market market, String code, String name) {
    String marketCode = alphaConfig.translateMarketCode(market);
    String symbol = code;
    if (marketCode != null) {
      symbol = symbol + "." + marketCode;
    }
    String result = alphaProxyCache.search(symbol, apiKey).get();
    AssetSearchResponse assetSearchResponse =
        objectMapper.readValue(result, AssetSearchResponse.class);
    if (assetSearchResponse.getData() == null || assetSearchResponse.getData().isEmpty()) {
      return null;
    }
    AssetSearchResult assetResult = assetSearchResponse.getData().iterator().next();
    return Asset.builder()
        .code(code.toUpperCase())
        .name(assetResult.getName())
        .priceSymbol(assetResult.getSymbol())
        .market(market)
        .marketCode(market.getCode().toUpperCase())
        .category(assetResult.getType())
        .build();
  }

  @Override
  public boolean canEnrich(Asset asset) {
    return asset.getName() == null;
  }
}
