package com.beancounter.marketdata.assets.figi;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import com.beancounter.marketdata.assets.AssetEnricher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class FigiEnricher implements AssetEnricher {
  private FigiProxy figiProxy;

  @Autowired(required = false)
  void setFigiProxy(FigiProxy figiProxy) {
    this.figiProxy = figiProxy;
  }

  //@Cacheable(value = "asset.ext") //, unless = "#result == null"
  public Asset enrich(Market market, String code, String name) {
    return figiProxy.find(market, code);
  }

  @Override
  public boolean canEnrich(Asset asset) {
    return asset.getName() == null;
  }

}
