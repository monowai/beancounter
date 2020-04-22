package com.beancounter.marketdata.assets.figi;

import com.beancounter.common.model.Asset;
import com.beancounter.common.model.Market;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j

public class FigiAdapter {

  public Asset transform(Market market, String assetCode, FigiAsset figiAsset) {
    return Asset.builder()
        .name(figiAsset.getName())
        .market(market)
        .marketCode(market.getCode())
        .code(assetCode)
        .category(figiAsset.getSecurityType2())
        .build();

  }
}
