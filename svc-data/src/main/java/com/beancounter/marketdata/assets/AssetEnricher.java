package com.beancounter.marketdata.assets;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.assets.figi.FigiProxy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AssetEnricher {
  private FigiProxy figiProxy;

  @Autowired(required = false)
  void setFigiProxy(FigiProxy figiProxy) {
    this.figiProxy = figiProxy;
  }

  //@Cacheable(value = "asset.ext") //, unless = "#result == null"
  public Asset enrich(String marketCode, String code) {
    if (figiProxy == null || marketCode.equalsIgnoreCase("MOCK")) {
      return null;
    }

    return figiProxy.find(marketCode, code);
  }


}
