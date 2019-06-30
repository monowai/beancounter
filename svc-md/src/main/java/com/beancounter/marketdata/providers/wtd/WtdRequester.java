package com.beancounter.marketdata.providers.wtd;

import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

/**
 * Async request to obtain MarketData.
 *
 * @author mikeh
 * @since 2019-03-06
 */

@Service
public class WtdRequester {

  private WtdRequest wtdRequest;

  @Autowired
  WtdRequester(WtdRequest wtdRequest) {
    this.wtdRequest = wtdRequest;
  }

  @Async
  @Cacheable("asset.prices")
  public Future<WtdResponse> getMarketData(String assets, String marketOpenDate, String apiKey) {
    WtdResponse result = wtdRequest.getMarketDataForAssets(assets, marketOpenDate, apiKey);
    return new AsyncResult<>(result);
  }
}
