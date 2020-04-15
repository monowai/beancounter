package com.beancounter.marketdata.assets.figi;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.markets.MarketService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class FigiProxy {

  private FigiGateway figiGateway;
  private final MarketService marketService;
  private final FigiConfig figiConfig;
  private FigiAdapter figiAdapter;


  FigiProxy(FigiConfig figiConfig, MarketService marketService) {
    this.figiConfig = figiConfig;
    this.marketService = marketService;
  }

  @Autowired
  void setFigiGateway(FigiGateway figiGateway) {
    this.figiGateway = figiGateway;
  }

  @Autowired
  void setFigiAdapter(FigiAdapter figiAdapter) {
    this.figiAdapter = figiAdapter;
  }

  @Cacheable("asset.ext")
  public Asset find(String marketCode, String assetCode) {
    if (figiConfig.getEnabled()) {
      Market market = marketService.getMarket(marketCode);
      String figiMarket = market.getAliases().get("FIGI");
      FigiSearch figiSearch = FigiSearch.builder()
          .exchCode(figiMarket.toUpperCase())
          .query(assetCode.toUpperCase())
          .build();
      FigiResponse response = figiGateway.search(figiSearch, figiConfig.getApiKey());
      if (response.getData() != null) {
        FigiAsset figiAsset = response.getData().iterator().next();
        return figiAdapter.transform(market, figiAsset);
      }
    }
    return null;
  }
}
