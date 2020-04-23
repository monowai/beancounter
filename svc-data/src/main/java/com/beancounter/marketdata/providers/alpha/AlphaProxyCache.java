package com.beancounter.marketdata.providers.alpha;

import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.AsyncResult;
import org.springframework.stereotype.Service;

/**
 * Async proxy to obtain MarketData from Alpha.
 *
 * @author mikeh
 * @since 2019-03-06
 */

@Service
public class AlphaProxyCache {

  private AlphaProxy alphaProxy;

  @Autowired(required = false)
  void setAlphaProxy(AlphaProxy alphaProxy) {
    this.alphaProxy = alphaProxy;
  }

  @Cacheable("asset.prices")
  @Async
  public Future<String> getPrice(String code, String apiKey) {
    return new AsyncResult<>(alphaProxy.getPrice(code, apiKey));
  }

  @SuppressWarnings("unused")
  @Cacheable("asset.prices")
  // Date is to support caching
  public String getPrices(String code, String date, String apiKey) {
    return alphaProxy.getPrices(code, apiKey);
  }
}
