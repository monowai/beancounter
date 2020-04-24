package com.beancounter.marketdata.assets.figi;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j

public class FigiAdapter {

  public Asset transform(Market market, String assetCode) {
    return Asset.builder()
        .market(market)
        .name(assetCode)
        .marketCode(market.getCode())
        .code(assetCode)
        .build();
  }

  public Asset transform(Market market, String assetCode, FigiAsset figiAsset) {
    Asset asset = transform(market, assetCode);
    asset.setName(figiAsset.getName());
    asset.setCategory(figiAsset.getSecurityType2());
    return asset;
  }
}
