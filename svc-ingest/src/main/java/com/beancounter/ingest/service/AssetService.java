package com.beancounter.ingest.service;

import com.beancounter.common.contracts.AssetRequest;
import com.beancounter.common.contracts.AssetResponse;
import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.common.utils.AssetUtils;
import com.beancounter.ingest.config.ExchangeConfig;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssetService {

  private ExchangeConfig exchangeConfig;
  private BcService bcService;
  private Map<String, Market> marketMap = new HashMap<>();

  @Autowired
  void setExchangeConfig(ExchangeConfig exchangeConfig) {
    this.exchangeConfig = exchangeConfig;
  }

  @Autowired
  void setBcService(BcService bcService) {
    this.bcService = bcService;
    Collection<Market> markets = bcService.getMarkets().getData();
    for (Market market : markets) {
      marketMap.put(market.getCode(), market);
    }
  }

  public Asset resolveAsset(String assetCode, String assetName, String marketCode) {
    if (marketCode.equalsIgnoreCase("MOCK")) {
      // Support unit testings where we don't really care about the asset
      return AssetUtils.getAsset(assetCode, "MOCK");
    }
    Market resolvedMarket = resolveMarket(marketCode);
    String callerKey = AssetUtils.toKey(assetCode, resolvedMarket.getCode());
    AssetRequest assetRequest = AssetRequest.builder()
        .asset(callerKey, Asset.builder()
            .code(assetCode)
            .name(assetName)
            .market(resolvedMarket)
            .build())
        .build();
    AssetResponse assetResponse = bcService.getAssets(assetRequest);
    if (assetResponse == null) {
      throw new BusinessException(
          String.format("No response returned for %s:%s", assetCode, marketCode));
    }

    return assetResponse.getAssets().values().iterator().next();

  }

  private Market resolveMarket(String marketCode) {
    Market market = marketMap.get(exchangeConfig.resolveAlias(marketCode));
    if (market == null) {
      throw new BusinessException(String.format("Unable to resolve market code %s", marketCode));
    }
    return market;
  }

}
