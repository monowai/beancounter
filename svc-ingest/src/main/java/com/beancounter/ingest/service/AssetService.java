package com.beancounter.ingest.service;

import com.beancounter.common.exception.BusinessException;
import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
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
    return Asset.builder()
        .code(assetCode)
        .name(assetName)
        .market(resolveMarket(marketCode))
        .build();

  }

  private Market resolveMarket(String marketCode) {
    Market market = marketMap.get(exchangeConfig.resolveAlias(marketCode));
    if (market == null) {
      throw new BusinessException(String.format("Unable to resolve market code %s", marketCode));
    }
    return market;
  }


}
