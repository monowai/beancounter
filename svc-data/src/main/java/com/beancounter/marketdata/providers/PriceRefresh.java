package com.beancounter.marketdata.providers;

import com.beancounter.common.model.Asset;
import com.beancounter.marketdata.assets.AssetService;
import com.beancounter.marketdata.service.MarketDataService;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class PriceRefresh {
  private final AssetService assetService;
  private MarketDataService marketDataService;
  PriceRefresh(AssetService assetService, MarketDataService marketDataService) {
    this.assetService = assetService;
    this.marketDataService = marketDataService;

  }

  @Transactional(readOnly = true)
  public void updatePrices() {
    log.info("Updating Prices " + new Date());
    AtomicInteger assetCount = new AtomicInteger();
    try (Stream<Asset> stream = assetService.findAllAssets()) {
      stream.forEach(
          asset -> {
            assetCount.getAndIncrement();
            marketDataService.getFuturePriceResponse(assetService.hydrateAsset(asset));
          });
    }
    log.info("Updated {} Prices on {}", assetCount.get(), new Date());
  }
}
