package com.beancounter.marketdata.providers.wtd;

import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

/**
 * Async proxy to obtain MarketData.
 *
 * @author mikeh
 * @since 2019-03-06
 */

@Service
public class WtdProxy {

  private WtdGateway wtdGateway;

  @Autowired(required = false)
  void setWtdGateway(WtdGateway wtdGateway) {
    this.wtdGateway = wtdGateway;
  }

  @Async
  @Cacheable("asset.prices")
  public Future<WtdResponse> getMarketData(String assets, String marketOpenDate, String apiKey) {
    WtdResponse result = wtdGateway.getMarketDataForAssets(assets, marketOpenDate, apiKey);
    return new AsyncResult<>(result);
  }
}
