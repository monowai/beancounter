package com.beancounter.marketdata.assets;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import org.springframework.stereotype.Service;

@Service
public class MockEnricher implements AssetEnricher {
  @Override
  public Asset enrich(Market market, String code, String name) {
    assert code != null;
    if (code.equalsIgnoreCase("BLAH")) {
      return null;
    }
    return Asset.builder()
        .marketCode(market.getCode())
        .market(market)
        .code(code.toUpperCase())
        .name(name.replace("\"", ""))
        .build();
  }

  @Override
  public boolean canEnrich(Asset asset) {
    return asset.getName() == null;
  }

}
