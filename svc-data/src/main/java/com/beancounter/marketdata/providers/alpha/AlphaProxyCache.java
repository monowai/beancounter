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
  // Date is to support caching
  @SuppressWarnings("unused")
  public Future<String> getCurrent(String code, String date, String apiKey) {
    return new AsyncResult<>(alphaProxy.getCurrent(code, apiKey));
  }

  @SuppressWarnings("unused")
  @Cacheable("asset.prices")
  @Async
  public Future<String> getHistoric(String code, String date, String apiKey) {
    return new AsyncResult<>(alphaProxy.getHistoric(code, apiKey));
  }

  @Cacheable("asset.search")
  @Async
  public Future<String> search(String symbol, String apiKey) {
    return new AsyncResult<>(alphaProxy.search(symbol, apiKey));
  }

}
